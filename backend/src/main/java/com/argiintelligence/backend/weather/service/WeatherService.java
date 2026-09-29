package com.argiintelligence.backend.weather.service;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.common.api.Provenance;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.weather.dto.WeatherResponse;
import com.argiintelligence.backend.weather.provider.WeatherProperties;
import com.argiintelligence.backend.weather.provider.WeatherProvider;
import com.argiintelligence.backend.weather.provider.WeatherProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves weather only from the real {@link WeatherProvider} (MASTER_SPEC §7). Spring Boot never generates weather
 * values: a provider failure is an error, never a fallback, and a report classified SYNTHETIC is refused.
 * Successful answers are cached for {@code WEATHER_CACHE_TTL}; failures are never cached.
 */
@Slf4j
@Service
public class WeatherService {

    // ponytail: the cache is only swept when it grows past this; a bounded LRU if many distinct points appear.
    private static final int SWEEP_THRESHOLD = 1_000;

    private final WeatherProvider provider;
    private final Duration ttl;
    private final Clock clock;
    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();
    private volatile Instant lastSuccessAt;
    private volatile String lastError;

    @Autowired
    public WeatherService(WeatherProvider provider, WeatherProperties props) {
        this(provider, props.cacheTtl(), Clock.systemUTC());
    }

    WeatherService(WeatherProvider provider, Duration ttl, Clock clock) {
        this.provider = provider;
        this.ttl = ttl;
        this.clock = clock;
    }

    /** @param farmId echoed in the response; null for a coordinate lookup */
    public WeatherResponse forLocation(UUID farmId, double latitude, double longitude, int days) {
        Key key = new Key(Math.round(latitude * 100), Math.round(longitude * 100), days);
        Instant now = clock.instant();
        Cached hit = cache.get(key);
        Cached entry = hit != null && hit.expiresAt().isAfter(now) ? hit : fetchAndCache(key, latitude, longitude,
                days, now);
        return toResponse(farmId, latitude, longitude, entry);
    }

    /** Weather at a farm's stored location. The caller has already checked the farm is visible to the user. */
    public WeatherResponse forFarm(FarmResponse farm, int days) {
        return forLocation(farm.id(), farm.location().latitude().doubleValue(),
                farm.location().longitude().doubleValue(), days);
    }

    public ProviderStatus providerStatus() {
        return new ProviderStatus(provider.name(), lastSuccessAt, lastError);
    }

    private Cached fetchAndCache(Key key, double latitude, double longitude, int days, Instant now) {
        WeatherProvider.Report report;
        try {
            report = provider.fetch(latitude, longitude, days);
        } catch (ApiException ex) {
            lastError = ex.getCode();
            throw ex;
        }
        // Product rule: the weather API never serves generated values, whatever a provider claims to be.
        if (report.currentClassification() == DataClassification.SYNTHETIC
                || report.dailyClassification() == DataClassification.SYNTHETIC) {
            log.error("Weather provider {} returned SYNTHETIC data; refusing to serve it", report.source());
            lastError = "UPSTREAM_INVALID_RESPONSE";
            throw WeatherProviderException.invalidResponse(report.source());
        }
        lastSuccessAt = now;
        lastError = null;
        Cached entry = new Cached(report, now, now.plus(ttl));
        if (cache.size() > SWEEP_THRESHOLD) {
            cache.values().removeIf(c -> !c.expiresAt().isAfter(now));
        }
        cache.put(key, entry);
        return entry;
    }

    private static WeatherResponse toResponse(UUID farmId, double latitude, double longitude, Cached entry) {
        WeatherProvider.Report r = entry.report();
        WeatherProvider.Current c = r.current();
        // A cached answer keeps its original retrievedAt (MASTER_SPEC §7.4).
        Provenance current = new Provenance(r.source(), r.currentClassification(), entry.retrievedAt(), null, null,
                null, null, null, null, List.copyOf(r.currentNotes()));
        Provenance daily = new Provenance(r.source(), r.dailyClassification(), entry.retrievedAt(), null, null, null,
                null, null, null, List.of());
        return new WeatherResponse(farmId, latitude, longitude,
                new WeatherResponse.Current(c.time(), c.temperatureC(), c.relativeHumidityPct(), c.precipitationMm(),
                        c.windSpeedKmh(), current),
                r.daily().stream().map(d -> new WeatherResponse.Daily(d.date(), d.minTemperatureC(),
                        d.maxTemperatureC(), d.precipitationMm(), d.precipitationProbabilityPct(),
                        d.relativeHumidityPct())).toList(),
                daily);
    }

    /** Last known provider state, for admin/actuator health; nothing here triggers a provider call. */
    public record ProviderStatus(String provider, Instant lastSuccessAt, String lastError) {
    }

    /** Coordinates rounded to 2 decimals (MASTER_SPEC §7.4). */
    private record Key(long latitude, long longitude, int days) {
    }

    private record Cached(WeatherProvider.Report report, Instant retrievedAt, Instant expiresAt) {
    }
}
