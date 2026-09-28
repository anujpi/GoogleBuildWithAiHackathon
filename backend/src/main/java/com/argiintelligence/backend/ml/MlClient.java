package com.argiintelligence.backend.ml;

import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/** The only place that talks HTTP to the ML service. Every failure becomes an {@link MlServiceException}. */
@Slf4j
@RequiredArgsConstructor
public class MlClient {

    private final RestClient http;

    /**
     * @param request ML request body. A plain map until the ML contract is final; swap for a record then.
     */
    public SupplyPredictionResponse predictSupply(Map<String, Object> request) {
        SupplyPredictionResponse response = post("/v1/predict/supply", request, SupplyPredictionResponse.class);
        if (response == null || response.modelVersion() == null || response.prediction() == null
                || response.prediction().value() == null || response.prediction().unit() == null) {
            log.warn("ML supply response missing required fields: {}", response);
            throw MlServiceException.badResponse();
        }
        return response;
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(type);
        } catch (ResourceAccessException ex) {
            log.warn("ML service unreachable on {}: {}", path, ex.getMessage());
            throw MlServiceException.unavailable();
        } catch (RestClientResponseException ex) {
            log.warn("ML service returned {} on {}", ex.getStatusCode(), path);
            throw MlServiceException.badResponse();
        } catch (RestClientException ex) {
            log.warn("ML service response on {} could not be read", path, ex);
            throw MlServiceException.badResponse();
        }
    }
}
