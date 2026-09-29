package com.argiintelligence.backend.weather.provider;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WeatherProperties.class)
class WeatherConfig {
}
