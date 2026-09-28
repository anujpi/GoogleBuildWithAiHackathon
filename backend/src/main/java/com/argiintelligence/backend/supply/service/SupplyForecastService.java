package com.argiintelligence.backend.supply.service;

import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.MlSupplyResponse;
import com.argiintelligence.backend.ml.exception.MlInvalidResponseException;
import com.argiintelligence.backend.supply.dto.SupplyForecastRequest;
import com.argiintelligence.backend.supply.dto.SupplyForecastResponse;
import com.argiintelligence.backend.supply.entity.ProductionHistory;
import com.argiintelligence.backend.supply.exception.InsufficientHistoryException;
import com.argiintelligence.backend.supply.exception.UnsupportedSeasonException;
import com.argiintelligence.backend.supply.repository.ProductionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the ML supply request from stored history and returns the ML result without altering it.
 * Not @Transactional on purpose: no database transaction is held open across the remote ML call.
 */
@Service
@RequiredArgsConstructor
public class SupplyForecastService {

    /** The ML contract accepts up to three previous crop years and requires cropYear - 1. */
    static final int MAX_HISTORY_YEARS = 3;
    /** Only these platform seasons have a verified equivalent in the training data. ZAID is deliberately absent. */
    static final Map<String, String> SEASON_TO_SOURCE_LABEL = Map.of("KHARIF", "Kharif", "RABI", "Rabi");
    static final String EXPECTED_UNIT = "tonnes";
    static final String TARGET_AREA_SOURCE = "REQUEST_INPUT";
    static final List<String> LIMITATIONS = List.of(
            "State-level forecast: no district, farm or field resolution.",
            "Trained on historical data for crop years 1997-2019; not a live or current-season forecast.",
            "Training data and history are a Kaggle third-party republication of official statistics.",
            "Units (hectares, tonnes) are as stated by the dataset publisher and not independently verified"
                    + " against the government source.",
            "predictionInterval is an 80% prediction interval from historical errors, not a confidence score.",
            "targetAreaHectares is a caller-supplied assumption, not observed data.");

    private final ProductionHistoryRepository productionHistory;
    private final MlClient mlClient;

    public SupplyForecastResponse forecast(SupplyForecastRequest request) {
        String season = request.season().strip().toUpperCase(Locale.ROOT);
        String sourceSeason = SEASON_TO_SOURCE_LABEL.get(season);
        if (sourceSeason == null) {
            throw new UnsupportedSeasonException(request.season().strip());
        }
        String state = request.state().strip();
        String crop = request.crop().strip();
        int cropYear = request.cropYear();

        List<ProductionHistory> history = productionHistory.findSeries(
                state, crop, sourceSeason, cropYear - MAX_HISTORY_YEARS, cropYear - 1);
        if (history.isEmpty() || history.getFirst().getCropYear() != cropYear - 1) {
            throw new InsufficientHistoryException(state, crop, season, cropYear,
                    productionHistory.findSeriesYears(state, crop, sourceSeason));
        }
        // Send the labels exactly as stored: the ML model expects the dataset's spelling.
        ProductionHistory latest = history.getFirst();
        MlSupplyRequest mlRequest = new MlSupplyRequest(latest.getCrop(), sourceSeason, cropYear,
                request.areaHectares(), history.stream()
                .map(h -> new MlSupplyRequest.HistoryPoint(h.getCropYear(), h.getAreaHectares(), h.getProductionTonnes()))
                .toList());

        MlSupplyResponse ml = mlClient.predictSupply(mlRequest);
        if (!EXPECTED_UNIT.equals(ml.prediction().unit())) {
            // Field names below promise tonnes; never relabel another unit silently.
            throw new MlInvalidResponseException("The ML service returned unit '" + ml.prediction().unit()
                    + "', expected '" + EXPECTED_UNIT + "'");
        }
        return toResponse(latest, season, sourceSeason, request, history, ml);
    }

    private static SupplyForecastResponse toResponse(ProductionHistory latest, String season, String sourceSeason,
                                                     SupplyForecastRequest request, List<ProductionHistory> history,
                                                     MlSupplyResponse ml) {
        MlSupplyResponse.Interval i = ml.prediction().interval();
        SupplyForecastResponse.PredictionInterval interval = i == null ? null
                : new SupplyForecastResponse.PredictionInterval(i.lower(), i.upper(), i.nominalCoverage(),
                i.testEmpiricalCoverage(), i.method());
        MlSupplyResponse.Provenance p = ml.provenance();
        return new SupplyForecastResponse(
                latest.getState(),
                latest.getCrop(),
                season,
                sourceSeason,
                request.cropYear(),
                request.areaHectares(),
                TARGET_AREA_SOURCE,
                new SupplyForecastResponse.Forecast(ml.prediction().value(), ml.prediction().period(),
                        ml.dataClassification(), interval, ml.generatedAt()),
                new SupplyForecastResponse.Baseline(ml.evidence().baselineValue(), ml.evidence().baselineMethod(),
                        ml.evidence().historyYearsUsed()),
                history.stream().map(h -> new SupplyForecastResponse.HistoryPoint(h.getCropYear(),
                        h.getAreaHectares(), h.getProductionTonnes(), h.getSource().name(),
                        h.getDataClassification().name(), h.getDatasetVersion())).toList(),
                new SupplyForecastResponse.Model(ml.modelName(), ml.modelVersion(), p.featureVersion(),
                        p.datasetVersion(), p.trainedAt(), p.trainingPeriod(), p.evaluationPeriod(),
                        p.trainingDataSource(), p.spatialGranularity()),
                LIMITATIONS);
    }
}
