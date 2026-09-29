package com.argiintelligence.backend.ml.dto;

import java.util.List;

/** ML {@code GET /v1/reference/scope}: exactly the combinations the served model supports. */
public record ScopeResponse(
        List<State> states,
        List<District> districts,
        List<Crop> crops,
        List<String> seasons,
        List<SupplySeries> supplySeries,
        List<MarketSeries> marketSeries,
        List<Dataset> datasets) {

    public record State(String stateId, String label) {
    }

    public record District(String districtId, String stateId, String label) {
    }

    public record Crop(String cropId, String label) {
    }

    public record SupplySeries(String districtId, String cropId, String season, int firstYear, int lastYear,
                               int yearsObserved) {
    }

    public record MarketSeries(String districtId, String cropId, String firstMonth, String lastMonth,
                               int monthsObserved) {
    }

    public record Dataset(String source, String datasetVersion, String dataThrough) {
    }
}
