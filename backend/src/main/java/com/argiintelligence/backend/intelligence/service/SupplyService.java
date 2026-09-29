package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.common.api.Provenance;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.intelligence.dto.SupplyEstimateResponse;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.dto.MlProvenance;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import com.argiintelligence.backend.reference.entity.RefCrop;
import com.argiintelligence.backend.reference.entity.RefDistrict;
import com.argiintelligence.backend.reference.entity.RefSupplySeries;
import com.argiintelligence.backend.reference.service.ReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Supply intelligence (MASTER_SPEC §8): a district x crop x season x crop-year production estimate. ML predicts;
 * this service checks the input against the synced scope, then passes the result on with labels, provenance and
 * the data-vintage caveats. It never computes or invents a value. MlClient has already validated the contract
 * rules (§8.1 step 4); an out-of-scope request never reaches ML.
 */
@Service
@RequiredArgsConstructor
public class SupplyService {

    static final String ML_SOURCE = "ML_SERVICE";
    static final String S01_SOURCE = "DES_S01_DATA_GOV_IN";

    private final MlClient mlClient;
    private final ReferenceService reference;

    public SupplyEstimateResponse supply(String districtId, String cropId, String season, int cropYear,
                                         Double areaHectares) {
        RefDistrict district = reference.district(districtId)
                .orElseThrow(() -> ApiException.unsupportedInput("districtId",
                        "District " + districtId + " is not supported"));
        RefCrop crop = reference.crop(cropId)
                .orElseThrow(() -> ApiException.unsupportedInput("cropId", "Crop " + cropId + " is not supported"));
        RefSupplySeries series = reference.series(districtId, cropId, season)
                .orElseThrow(() -> ApiException.unsupportedInput("season", "No reported " + crop.getLabel()
                        + " series for " + district.getLabel() + " in season " + season));
        if (!series.isEstimable(cropYear)) {
            throw ApiException.unsupportedInput("cropYear", "Estimable crop years for this series are "
                    + (series.getFirstYear() + 1) + "-" + series.latestEstimableYear());
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
                new SupplyEstimateResponse.Target(districtId, district.getLabel(), cropId, crop.getLabel(), season,
                        cropYear),
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
}
