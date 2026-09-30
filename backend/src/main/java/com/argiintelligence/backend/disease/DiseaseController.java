package com.argiintelligence.backend.disease;

import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.ml.MlClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Leaf-image diagnosis. The ML service classifies; this controller adds the platform's handling policy (when to ask
 * for expert confirmation) and the model's known limits. It never changes the ML prediction or its probability.
 */
@RestController
@RequestMapping("/api/disease")
@RequiredArgsConstructor
public class DiseaseController {

    /** Below this top-class probability the result is shown as "uncertain, confirm with an expert". A product policy,
     * not a calibrated threshold: the model's probabilities are uncalibrated softmax outputs. */
    static final double REVIEW_BELOW = 0.70;
    static final Set<String> TYPES = Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE);
    static final List<String> LIMITATIONS = List.of(
            "Trained on PlantVillage leaf photos (mostly single leaves on plain backgrounds); accuracy on field "
                    + "photos with clutter, shadows or several leaves is expected to be lower and was not measured.",
            "Only Potato and Tomato classes are covered; any other crop will still be forced into one of them.",
            "The probability is an uncalibrated softmax output, not a verified confidence.",
            "A diagnosis should be confirmed by an agronomist/KVK before spraying or other treatment.");

    private final MlClient mlClient;

    public record Diagnosis(JsonNode ml, boolean needsExpertReview, String reviewPolicy, String cropWarning,
                            List<String> limitations) {
    }

    @PostMapping(path = "/diagnose", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Diagnosis diagnose(@RequestParam("image") MultipartFile image,
                              @RequestParam(value = "crop", required = false) String crop) throws IOException {
        if (image.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED", "Upload a JPEG or PNG leaf photo");
        }
        if (image.getContentType() == null || !TYPES.contains(image.getContentType())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE",
                    "Only JPEG and PNG images are supported");
        }
        JsonNode ml = MlClient.requireFields(
                mlClient.predictDisease(image.getBytes(), image.getOriginalFilename(), image.getContentType()),
                "modelName", "modelVersion", "prediction.crop", "prediction.disease", "prediction.probability",
                "provenance");
        double p = ml.get("prediction").get("probability").asDouble();
        String warning = null;
        if (crop != null && !crop.isBlank()) {
            List<String> supported = new ArrayList<>();
            ml.get("supportedCrops").forEach(c -> supported.add(c.asString()));
            if (supported.stream().noneMatch(c -> c.equalsIgnoreCase(crop.strip()))) {
                warning = "The model does not cover " + crop.strip() + " (supported: " + String.join(", ", supported)
                        + "); this result is not meaningful for that crop.";
            }
        }
        return new Diagnosis(ml, p < REVIEW_BELOW, "expert review suggested when top probability < " + REVIEW_BELOW
                + " (platform policy, not calibrated)", warning, LIMITATIONS);
    }
}
