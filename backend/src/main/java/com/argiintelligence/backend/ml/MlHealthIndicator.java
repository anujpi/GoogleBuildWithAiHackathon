package com.argiintelligence.backend.ml;

import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.ml.dto.MlHealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

/**
 * Actuator "ml" component (MASTER_SPEC §6.8): UP when the service answers and every model is READY, otherwise
 * DEGRADED. Never DOWN: the backend keeps serving everything that does not need ML.
 */
@Component("ml")
@RequiredArgsConstructor
class MlHealthIndicator implements HealthIndicator {

    static final Status DEGRADED = new Status("DEGRADED");

    private final MlClient ml;

    @Override
    public Health health() {
        try {
            MlHealthResponse r = ml.health();
            boolean ready = !r.models().isEmpty() && r.models().stream().allMatch(m -> "READY".equals(m.status()));
            return Health.status(ready ? Status.UP : DEGRADED).build();
        } catch (ApiException ex) {
            return Health.status(DEGRADED).build();
        }
    }
}
