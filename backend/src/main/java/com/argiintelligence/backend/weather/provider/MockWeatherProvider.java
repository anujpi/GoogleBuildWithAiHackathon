package com.argiintelligence.backend.weather.provider;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.weather.dto.WeatherResponse.Current;
import com.argiintelligence.backend.weather.dto.WeatherResponse.Daily;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Deterministic synthetic weather: the same point and day always give the same values, so demos are stable.
 * Everything it returns is labelled SYNTHETIC. Ranges are loosely typical of peninsular India, nothing more.
 */
// ponytail: only provider; add a real one (e.g. Open-Meteo, no API key) behind WeatherProvider plus a property switch.
@Component
public class MockWeatherProvider implements WeatherProvider {

    public static final String SOURCE = "MOCK_WEATHER_PROVIDER";

    private final Clock clock = Clock.systemUTC();

    @Override
    public Report fetch(double latitude, double longitude, int days) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Random r = seeded(latitude, longitude, today);
        Current current = new Current(now, round(22 + r.nextDouble() * 12), round(45 + r.nextDouble() * 45),
                round(r.nextDouble() < 0.3 ? r.nextDouble() * 8 : 0), round(4 + r.nextDouble() * 18));
        List<Daily> daily = IntStream.range(0, days).mapToObj(i -> day(latitude, longitude, today.plusDays(i))).toList();
        return new Report(SOURCE, DataClassification.SYNTHETIC, null, current, daily);
    }

    private static Daily day(double latitude, double longitude, LocalDate date) {
        Random r = seeded(latitude, longitude, date);
        double min = 16 + r.nextDouble() * 8;
        double rainProbability = r.nextDouble() * 100;
        double rain = rainProbability > 55 ? r.nextDouble() * 30 : 0;
        return new Daily(date, round(min), round(min + 6 + r.nextDouble() * 8), round(rain), round(rainProbability),
                round(40 + r.nextDouble() * 50));
    }

    private static Random seeded(double latitude, double longitude, LocalDate date) {
        return new Random(Double.hashCode(latitude) * 31L + Double.hashCode(longitude) * 17L + date.toEpochDay());
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
