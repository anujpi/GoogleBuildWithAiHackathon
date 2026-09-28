package com.argiintelligence.backend.supply.exception;

import com.argiintelligence.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class UnsupportedSeasonException extends ApiException {

    public UnsupportedSeasonException(String season) {
        super(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SEASON",
                "Season '" + season + "' is not supported for supply forecasts; supported: KHARIF, RABI");
    }
}
