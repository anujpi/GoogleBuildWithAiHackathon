package com.argiintelligence.backend.farm.exception;

import com.argiintelligence.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class FarmNotFoundException extends ApiException {

    public FarmNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "Farm " + id + " not found");
    }
}
