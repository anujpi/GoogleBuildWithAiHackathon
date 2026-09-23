package com.argiintelligence.backend.farm.dto;

import com.argiintelligence.backend.farm.entity.AreaUnit;
import com.argiintelligence.backend.farm.entity.IrrigationType;
import com.argiintelligence.backend.farm.entity.Season;
import com.argiintelligence.backend.farm.entity.SoilDataClassification;
import com.argiintelligence.backend.farm.entity.SoilDataSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Body for both POST /api/farms and PUT /api/farms/{id}. PUT replaces every field; a null soilProfile removes it. */
public record FarmRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Positive BigDecimal area,
        @NotNull AreaUnit areaUnit,
        @NotNull IrrigationType irrigationType,
        @Size(max = 100) String currentCrop,
        @Size(max = 100) String previousCrop,
        @NotNull Season season,
        @NotNull @Valid Location location,
        @Valid Soil soilProfile) {

    public record Location(
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotBlank @Size(max = 100) String state,
            @NotBlank @Size(max = 100) String district,
            @Size(max = 100) String taluk,
            @Size(max = 255) String addressLabel) {
    }

    /** Measurements are optional; omitted values stay null. */
    public record Soil(
            @DecimalMin("0") @DecimalMax("14") BigDecimal ph,
            @PositiveOrZero BigDecimal electricalConductivity,
            @PositiveOrZero BigDecimal organicCarbon,
            @PositiveOrZero BigDecimal nitrogen,
            @PositiveOrZero BigDecimal phosphorus,
            @PositiveOrZero BigDecimal potassium,
            @PositiveOrZero BigDecimal sulphur,
            @PositiveOrZero BigDecimal zinc,
            @PositiveOrZero BigDecimal iron,
            @PositiveOrZero BigDecimal manganese,
            @PositiveOrZero BigDecimal copper,
            @PositiveOrZero BigDecimal boron,
            @NotNull SoilDataSource source,
            @NotNull SoilDataClassification dataClassification,
            @PastOrPresent LocalDate measuredAt,
            @DecimalMin("0") @DecimalMax("1") BigDecimal confidence) {
    }
}
