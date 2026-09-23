package com.argiintelligence.backend.farm.exception;

import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.entity.SoilDataClassification;
import com.argiintelligence.backend.farm.entity.SoilDataSource;
import org.springframework.http.HttpStatus;

public class InconsistentSoilProvenanceException extends ApiException {

    public InconsistentSoilProvenanceException(SoilDataSource source, SoilDataClassification classification) {
        super(HttpStatus.BAD_REQUEST, "INCONSISTENT_SOIL_PROVENANCE",
                "Soil data from " + source + " cannot be classified as " + classification);
    }
}
