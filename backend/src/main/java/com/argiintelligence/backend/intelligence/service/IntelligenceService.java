package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.api.ApiError.FieldViolation;
import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.common.api.Provenance;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.intelligence.dto.SupplyEstimateResponse;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.dto.MlProvenance;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.ScopeResponse;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Supply intelligence (MASTER_SPEC §8). ML predicts; this service checks scope, then passes the result on with
 * labels, provenance and the data-vintage caveats. It never computes or invents a value.
 *
 * <p>INTERIM: the scope pre-check reads the ML scope live. MASTER_SPEC §6.3 moves it to the synced reference
 * tables (V4, phase P2); swap the {@code mlClient.scope()} call for the reference repository then.
 */
@Service
@RequiredArgsConstructor
public class IntelligenceService {

    static final String ML_SOURCE = "ML_SERVICE";
    static final String S01_SOURCE = "DES_S01_DATA_GOV_IN";

    private final MlClient mlClient;

    public SupplyEstimateResponse supply(String districtId, String cropId, String season, int cropYear,
                                         Double areaHectares) {
        ScopeResponse scope = mlClient.scope();
        ScopeResponse.District district = scope.districts().stream()
                .filter(d -> d.districtId().equals(districtId)).findFirst()
                .orElseThrow(() -> unsupported("districtId", "District " + districtId + " is not supported"));
        ScopeResponse.Crop crop = scope.crops().stream()
                .filter(c -> c.cropId().equals(cropId)).findFirst()
                .orElseThrow(() -> unsupported("cropId", "Crop " + cropId + " is not supported"));
        ScopeResponse.SupplySeries series = scope.supplySeries().stream()
                .filter(s -> s.districtId().equals(districtId) && s.cropId().equals(cropId)
                        && s.season().equals(season)).findFirst()
                .orElseThrow(() -> unsupported("season", "No reported " + crop.label() + " series for "
                        + district.label() + " in season " + season));
        if (cropYear < series.firstYear() + 1 || cropYear > series.lastYear() + 1) {
            throw unsupported("cropYear", "Estimable crop years for this series are " + (series.firstYear() + 1)
                    + "-" + (series.lastYear() + 1));
        }

        SupplyPredictionResponse ml = mlClient.predictSupply(
                new MlSupplyRequest(districtId, cropId, season, cropYear, areaHectares));
        Instant retrievedAt = Instant.now();
        var e = ml.estimate();
        MlProvenance mp = e.provenance();
        MlProvenance hp = ml.history().provenance();

        List<String> limitations = new ArrayList<>(ml.limitations());
        limitations.add("Data ends in crop year " + mp.dataThrough() + "; this is not a current-season forecast.");
        if ("BASELINE".equals(e.servedMethod())) {
            limitations.add("The trained model did not beat the baseline; the baseline estimate is served.");
        }
        return new SupplyEstimateResponse(
                new SupplyEstimateResponse.Target(districtId, district.label(), cropId, crop.label(), season, cropYear),
                new SupplyEstimateResponse.Estimate(e.production(), e.yieldValue(), e.area(), e.servedMethod(),
                        e.historyYearsUsed()),
                ml.baseline(), ml.reported(),
                new SupplyEstimateResponse.History(ml.history().units(), ml.history().points()),
                ml.historicalYieldStats(), ml.modelEvaluation(),
                new Provenance(ML_SOURCE, mp.dataClassification(), retrievedAt, mp.generatedAt(), mp.datasetVersion(),
                        mp.modelName(), mp.modelVersion(), mp.featureVersion(), mp.dataThrough(), List.of()),
                new Provenance(S01_SOURCE, DataClassification.OBSERVED, null, null, hp.datasetVersion(), null, null,
                        null, hp.dataThrough(), List.of()),
                List.copyOf(limitations));
    }

    private static ApiException unsupported(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "UNSUPPORTED_INPUT", message,
                List.of(new FieldViolation(field, message)));
    }
}
