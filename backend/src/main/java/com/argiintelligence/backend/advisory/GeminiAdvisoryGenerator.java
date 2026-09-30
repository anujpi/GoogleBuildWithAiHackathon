package com.argiintelligence.backend.advisory;

import com.argiintelligence.backend.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Gemini via the REST generateContent API with a JSON response schema. Called only from the backend. */
@Slf4j
@Component
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiAdvisoryGenerator implements AdvisoryGenerator {

    static final String SYSTEM_PROMPT = """
            You are an agricultural extension assistant for small farmers in India.
            You receive one JSON object called EVIDENCE, produced by the platform's data pipelines, ML models and
            rule-based decision engine. Your only job is to explain that evidence in simple, practical language.

            Strict rules:
            1. Use ONLY facts, numbers, crops, diseases, dates and sources present in EVIDENCE. Never invent or
               estimate sensor readings, weather values, prices, yields, forecasts, disease diagnoses or sources.
            2. If a section's status is UNAVAILABLE or a value is null, say that information is unavailable.
            3. Do not change or override the decision in EVIDENCE.decision; explain it.
            4. Keep the data labels honest: a FORECAST or MODEL_PREDICTION is not a certainty; demand is a proxy;
               the suitability index is not a probability; historical data ends in 2019/2020.
            5. There is no mandi price data in EVIDENCE; do not mention prices as numbers.
            6. Recommended actions must be general, safe agronomic steps grounded in EVIDENCE (e.g. confirm a
               diagnosis with the local KVK), never specific pesticide doses.
            7. Write numbers with Western digits (0-9). You may round numbers, e.g. 9,919,778 t -> about 99 lakh t.
            8. Write the whole answer in %s.
            """;

    private final GeminiProperties properties;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public GeminiAdvisoryGenerator(GeminiProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    @Override
    public String id() {
        return "GEMINI";
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public boolean available() {
        return properties.apiKey() != null && !properties.apiKey().isBlank();
    }

    @Override
    public AdvisoryText generate(String evidenceJson, String language) {
        Map<String, Object> stringArray = Map.of("type", "ARRAY", "items", Map.of("type", "STRING"));
        Map<String, Object> schema = Map.of("type", "OBJECT",
                "properties", Map.of("explanation", Map.of("type", "STRING"), "keyFactors", stringArray,
                        "recommendedActions", stringArray, "uncertainty", stringArray),
                "required", List.of("explanation", "keyFactors", "recommendedActions", "uncertainty"),
                "propertyOrdering", List.of("explanation", "keyFactors", "recommendedActions", "uncertainty"));
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text",
                        SYSTEM_PROMPT.formatted("hi".equals(language) ? "Hindi (Devanagari script)" : "English")))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text",
                        "EVIDENCE:\n" + evidenceJson + "\n\nExplain this for the farmer. explanation: 4-6 sentences. "
                                + "keyFactors: 3-6 items. recommendedActions: 3-5 items. uncertainty: 2-4 items.")))),
                "generationConfig", Map.of("temperature", 0.2, "responseMimeType", "application/json",
                        "responseSchema", schema));
        JsonNode res;
        try {
            res = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsBytes(body))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            // The message can include the response body but never the key (it is sent as a header).
            log.warn("Gemini call failed: {}", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM_UNAVAILABLE", "Gemini request failed");
        }
        try {
            String text = res.get("candidates").get(0).get("content").get("parts").get(0).get("text").asString();
            JsonNode out = jsonMapper.readTree(text);
            return new AdvisoryText(out.get("explanation").asString(), list(out.get("keyFactors")),
                    list(out.get("recommendedActions")), list(out.get("uncertainty")));
        } catch (RuntimeException ex) {
            log.warn("Gemini returned an unexpected body: {}", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM_INVALID_RESPONSE", "Gemini returned no usable text");
        }
    }

    private static List<String> list(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null) {
            arr.forEach(n -> out.add(n.asString()));
        }
        return out;
    }
}
