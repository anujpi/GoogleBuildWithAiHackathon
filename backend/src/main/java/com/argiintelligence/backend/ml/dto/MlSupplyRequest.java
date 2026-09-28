package com.argiintelligence.backend.ml.dto;

import java.math.BigDecimal;
import java.util.List;

/** Body of ML {@code POST /v1/predict/supply} (ml-service/docs/ml-contracts/supply.md). */
public record MlSupplyRequest(
        String crop,
        String season,
        int cropYear,
        BigDecimal areaHectares,
        List<HistoryPoint> history) {

    public record HistoryPoint(int cropYear, BigDecimal areaHectares, BigDecimal productionTonnes) {
    }
}
