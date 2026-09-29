package com.argiintelligence.backend.admin.service;

import com.argiintelligence.backend.admin.dto.AdminDtos.MlModelsResponse;
import com.argiintelligence.backend.admin.dto.AdminDtos.SystemHealthResponse;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.MlServiceProperties;
import com.argiintelligence.backend.ml.dto.MlHealthResponse;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.ModelEvaluation;
import com.argiintelligence.backend.reference.dto.ReferenceScopeResponse;
import com.argiintelligence.backend.reference.entity.ReferenceSync;
import com.argiintelligence.backend.reference.service.ReferenceService;
import com.argiintelligence.backend.weather.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * System, ML and provider health plus model/dataset metadata for admins (MASTER_SPEC §6.7). Everything reported
 * comes from a real check or real metadata; values nobody reports stay null.
 */
@Slf4j
@Service
public class AdminSystemService {

    static final String UP = "UP";
    static final String DOWN = "DOWN";
    static final String DEGRADED = "DEGRADED";
    /** Lucknow: a fixed in-scope point used only to probe the weather provider when it has not been used yet. */
    private static final double PROBE_LATITUDE = 26.85;
    private static final double PROBE_LONGITUDE = 80.95;

    private final JdbcTemplate jdbc;
    private final MlClient ml;
    private final MlServiceProperties mlProperties;
    private final WeatherService weather;
    private final ReferenceService reference;
    private final ObjectProvider<BuildProperties> build;

    public AdminSystemService(JdbcTemplate jdbc, MlClient ml, MlServiceProperties mlProperties, WeatherService weather,
                              ReferenceService reference, ObjectProvider<BuildProperties> build) {
        this.jdbc = jdbc;
        this.ml = ml;
        this.mlProperties = mlProperties;
        this.weather = weather;
        this.reference = reference;
        this.build = build;
    }

    public SystemHealthResponse health() {
        return new SystemHealthResponse(database(), mlService(), weatherProvider(), referenceSync(),
                new SystemHealthResponse.Build(build.stream().findFirst().map(BuildProperties::getVersion)
                        .orElse(null)));
    }

    public MlModelsResponse models() {
        List<ReferenceScopeResponse.Dataset> datasets = reference.scope().datasets();
        MlHealthResponse health;
        try {
            health = ml.health();
        } catch (ApiException ex) {
            return new MlModelsResponse(ex.getCode(), List.of(), datasets, null);
        }
        return new MlModelsResponse(null, health.models(), datasets, supplyEvaluation(health));
    }

    /**
     * The evaluation is model-level metadata that ML returns with every supply estimate; it is read from one real
     * estimate for the first synced series. Null when no model is READY, no series is synced, or ML refuses.
     */
    private ModelEvaluation supplyEvaluation(MlHealthResponse health) {
        boolean ready = health.models().stream()
                .anyMatch(m -> "SUPPLY".equals(m.capability()) && "READY".equals(m.status()));
        var series = reference.scope().supplySeries();
        if (!ready || series.isEmpty()) {
            return null;
        }
        var s = series.getFirst();
        try {
            return ml.predictSupply(new MlSupplyRequest(s.districtId(), s.cropId(), s.season(), s.lastYear() + 1,
                    null)).modelEvaluation();
        } catch (ApiException ex) {
            log.warn("Could not read the supply model evaluation: {}", ex.getCode());
            return null;
        }
    }

    private SystemHealthResponse.Component database() {
        try {
            jdbc.queryForObject("select 1", Integer.class);
            return new SystemHealthResponse.Component(UP);
        } catch (RuntimeException ex) {
            log.warn("Database health check failed", ex);
            return new SystemHealthResponse.Component(DOWN);
        }
    }

    private SystemHealthResponse.MlService mlService() {
        long start = System.nanoTime();
        try {
            MlHealthResponse health = ml.health();
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            boolean allReady = health.models().stream().allMatch(m -> "READY".equals(m.status()));
            return new SystemHealthResponse.MlService(allReady && !health.models().isEmpty() ? UP : DEGRADED,
                    mlProperties.baseUrl(), latencyMs, health.models());
        } catch (ApiException ex) {
            return new SystemHealthResponse.MlService(DOWN, mlProperties.baseUrl(), null, List.of());
        }
    }

    private SystemHealthResponse.WeatherProviderHealth weatherProvider() {
        WeatherService.ProviderStatus status = weather.providerStatus();
        if (status.lastSuccessAt() == null && status.lastError() == null) {
            // Never used since startup: probe once (served from the cache afterwards).
            try {
                weather.forLocation(null, PROBE_LATITUDE, PROBE_LONGITUDE, 1);
            } catch (ApiException ignored) {
                // Recorded as lastError by the service.
            }
            status = weather.providerStatus();
        }
        String state = status.lastError() == null ? UP : status.lastSuccessAt() == null ? DOWN : DEGRADED;
        return new SystemHealthResponse.WeatherProviderHealth(state, status.provider(), status.lastSuccessAt(),
                status.lastError());
    }

    private SystemHealthResponse.ReferenceSyncHealth referenceSync() {
        return new SystemHealthResponse.ReferenceSyncHealth(
                reference.lastSuccessfulSync().map(ReferenceSync::getSyncedAt).orElse(null),
                reference.lastSync().map(s -> s.getStatus().name()).orElse(null));
    }
}
