package com.argiintelligence.backend.ml.exception;

import com.argiintelligence.backend.common.api.ApiError.FieldViolation;
import com.argiintelligence.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.List;

/** The ML service answered 422: it cannot predict for these inputs. {@code details} carries its reasons. */
public class MlRequestRejectedException extends ApiException {

    public MlRequestRejectedException(List<FieldViolation> details) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "ML_REQUEST_REJECTED",
                "The ML service cannot produce a prediction for these inputs", details);
    }
}
