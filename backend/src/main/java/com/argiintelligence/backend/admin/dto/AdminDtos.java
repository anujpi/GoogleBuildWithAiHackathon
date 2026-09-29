package com.argiintelligence.backend.admin.dto;

import com.argiintelligence.backend.ml.dto.MlHealthResponse;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.ModelEvaluation;
import com.argiintelligence.backend.reference.dto.ReferenceScopeResponse;
import com.argiintelligence.backend.user.entity.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the admin API (MASTER_SPEC §6.7). */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** PATCH /api/admin/users/{id}: either field may be omitted (null = unchanged). */
    public record UserPatchRequest(Role role, Boolean enabled) {
    }

    /** PUT /api/admin/users/{id}/districts: the complete new set (replaces the old one). */
    public record DistrictAssignmentRequest(
            @NotNull List<@NotNull @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,49}",
                    message = "must be a lower-case district id") String> districtIds) {
    }

    /** POST /api/admin/farms/{id}/owner. */
    public record FarmOwnerRequest(@NotNull UUID userId) {
    }

    /** Status is UP, DOWN or DEGRADED (§6.7). Unknown values are null, never invented. */
    public record SystemHealthResponse(Component database, MlService mlService, WeatherProviderHealth weatherProvider,
                                       ReferenceSyncHealth referenceSync, Build build) {

        public record Component(String status) {
        }

        public record MlService(String status, String url, Long latencyMs, List<MlHealthResponse.Model> models) {
        }

        public record WeatherProviderHealth(String status, String provider, Instant lastSuccessAt, String lastError) {
        }

        public record ReferenceSyncHealth(Instant lastSyncedAt, String status) {
        }

        public record Build(String version) {
        }
    }

    /**
     * GET /api/admin/ml/models. {@code mlError} is the §13 code when ML could not be reached (models then empty);
     * {@code modelEvaluation} is the served supply model's evaluation when ML can return it, else null.
     */
    public record MlModelsResponse(String mlError, List<MlHealthResponse.Model> models,
                                   List<ReferenceScopeResponse.Dataset> datasets, ModelEvaluation modelEvaluation) {
    }
}
