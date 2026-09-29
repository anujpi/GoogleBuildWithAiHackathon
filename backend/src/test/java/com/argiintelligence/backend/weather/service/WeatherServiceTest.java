package com.argiintelligence.backend.weather.service;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.weather.dto.WeatherResponse;
import com.argiintelligence.backend.weather.provider.WeatherProvider;
import com.argiintelligence.backend.weather.provider.WeatherProviderException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cache, SYNTHETIC guard and provider-status tracking (MASTER_SPEC §7.1, §7.4), with a scripted test provider. */
class WeatherServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-29T08:00:00Z");

    private final MutableClock clock = new MutableClock(T0);
    private final ScriptedProvider provider = new ScriptedProvider();
    private final WeatherService service = new WeatherService(provider, Duration.ofMinutes(30), clock);

    @Test
    void mapsSplitProvenance() {
        provider.next(() -> report(DataClassification.ESTIMATED, DataClassification.FORECAST));
        WeatherResponse r = service.forLocation(null, 26.85, 80.95, 1);

        assertThat(r.current().provenance().dataClassification()).isEqualTo(DataClassification.ESTIMATED);
        assertThat(r.current().provenance().notes()).containsExactly("fixture note");
        assertThat(r.dailyProvenance().dataClassification()).isEqualTo(DataClassification.FORECAST);
        assertThat(r.current().provenance().retrievedAt()).isEqualTo(T0);
    }

    @Test
    void cacheHitKeepsTheOriginalRetrievedAtAndRoundsCoordinates() {
        provider.next(() -> report(DataClassification.ESTIMATED, DataClassification.FORECAST));
        service.forLocation(null, 26.851, 80.951, 1);
        clock.advance(Duration.ofMinutes(10));

        WeatherResponse cached = service.forLocation(null, 26.849, 80.949, 1);

        assertThat(provider.calls).isEqualTo(1);
        assertThat(cached.dailyProvenance().retrievedAt()).isEqualTo(T0);
    }

    @Test
    void expiredEntryIsFetchedAgain() {
        provider.next(() -> report(DataClassification.ESTIMATED, DataClassification.FORECAST));
        provider.next(() -> report(DataClassification.ESTIMATED, DataClassification.FORECAST));
        service.forLocation(null, 26.85, 80.95, 1);
        clock.advance(Duration.ofMinutes(31));

        WeatherResponse fresh = service.forLocation(null, 26.85, 80.95, 1);

        assertThat(provider.calls).isEqualTo(2);
        assertThat(fresh.dailyProvenance().retrievedAt()).isEqualTo(T0.plus(Duration.ofMinutes(31)));
    }

    @Test
    void failuresAreNeverCachedAndAreRecordedInTheStatus() {
        provider.next(() -> {
            throw WeatherProviderException.unavailable("Test");
        });
        provider.next(() -> report(DataClassification.ESTIMATED, DataClassification.FORECAST));

        assertThatThrownBy(() -> service.forLocation(null, 26.85, 80.95, 1))
                .isInstanceOf(WeatherProviderException.class);
        assertThat(service.providerStatus().lastError()).isEqualTo("UPSTREAM_UNAVAILABLE");

        service.forLocation(null, 26.85, 80.95, 1);
        assertThat(provider.calls).isEqualTo(2);
        assertThat(service.providerStatus().lastError()).isNull();
        assertThat(service.providerStatus().lastSuccessAt()).isEqualTo(T0);
    }

    @Test
    void syntheticReportsAreRefusedAndNotCached() {
        provider.next(() -> report(DataClassification.SYNTHETIC, DataClassification.FORECAST));
        assertThatThrownBy(() -> service.forLocation(null, 26.85, 80.95, 1))
                .isInstanceOfSatisfying(WeatherProviderException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("UPSTREAM_INVALID_RESPONSE"));

        provider.next(() -> report(DataClassification.ESTIMATED, DataClassification.SYNTHETIC));
        assertThatThrownBy(() -> service.forLocation(null, 26.85, 80.95, 1))
                .isInstanceOf(WeatherProviderException.class);
        assertThat(provider.calls).isEqualTo(2);
    }

    private static WeatherProvider.Report report(DataClassification current, DataClassification daily) {
        return new WeatherProvider.Report("TEST_SOURCE",
                new WeatherProvider.Current(OffsetDateTime.of(2026, 9, 29, 13, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)),
                        27.0, 60.0, 0.0, null),
                current, List.of("fixture note"),
                List.of(new WeatherProvider.Day(LocalDate.parse("2026-09-29"), 19.0, 30.0, null, 20.0, 55.0)),
                daily);
    }

    private static final class ScriptedProvider implements WeatherProvider {
        private final Deque<Supplier<Report>> script = new ArrayDeque<>();
        int calls;

        void next(Supplier<Report> answer) {
            script.add(answer);
        }

        @Override
        public Report fetch(double latitude, double longitude, int days) {
            calls++;
            return script.removeFirst().get();
        }

        @Override
        public String name() {
            return "TEST_SOURCE";
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
