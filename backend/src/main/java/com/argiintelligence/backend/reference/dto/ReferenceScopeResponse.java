package com.argiintelligence.backend.reference.dto;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/reference/scope (MASTER_SPEC §6.3), served from the synced tables. {@code syncedAt} is null (and the
 * lists empty) until a sync has succeeded.
 */
public record ReferenceScopeResponse(
        List<State> states,
        List<District> districts,
        List<Crop> crops,
        List<String> seasons,
        List<SupplySeries> supplySeries,
        List<Dataset> datasets,
        Instant syncedAt) {

    public record State(String stateId, String label) {
    }

    public record District(String districtId, String stateId, String label) {
    }

    public record Crop(String cropId, String label) {
    }

    /** {@code estimableYears} = firstYear+1 … lastYear+1: candidate years; ML is the final authority. */
    public record SupplySeries(String districtId, String cropId, String season, int firstYear, int lastYear,
                               int yearsObserved, List<Integer> estimableYears) {
    }

    public record Dataset(String source, String datasetVersion, String dataThrough) {
    }
}
