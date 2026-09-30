package com.argiintelligence.backend.advisory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Google Gemini API (generativelanguage.googleapis.com). The key comes from GEMINI_API_KEY and is never logged. */
@ConfigurationProperties("app.gemini")
public record GeminiProperties(
        String apiKey,
        @DefaultValue("gemini-2.5-flash") String model,
        @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
        @DefaultValue("PT5S") Duration connectTimeout,
        @DefaultValue("PT40S") Duration readTimeout) {
}
