package com.argiintelligence.backend.supply.exception;

import com.argiintelligence.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.List;

/** The ML model needs the previous crop year's observed area and production; without it no forecast is made. */
public class InsufficientHistoryException extends ApiException {

    public InsufficientHistoryException(String state, String crop, String season, int cropYear,
                                        List<Integer> recordedYears) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_HISTORY", message(state, crop, season, cropYear,
                recordedYears));
    }

    private static String message(String state, String crop, String season, int cropYear, List<Integer> years) {
        String series = crop + " / " + season + " / " + state;
        if (years.isEmpty()) {
            return "No production history exists for " + series;
        }
        return "No observed production history for " + series + " in crop year " + (cropYear - 1)
                + ", which a forecast for " + cropYear + " requires; recorded years span "
                + years.getFirst() + "-" + years.getLast() + " (" + years.size() + " years)";
    }
}
