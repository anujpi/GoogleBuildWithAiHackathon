package com.argiintelligence.backend.farm.dto;

import com.argiintelligence.backend.farm.entity.AreaUnit;
import com.argiintelligence.backend.farm.entity.IrrigationType;
import com.argiintelligence.backend.farm.entity.Season;
import com.argiintelligence.backend.farm.entity.SoilDataClassification;
import com.argiintelligence.backend.farm.entity.SoilDataSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FarmResponse(
        UUID id,
        String name,
        BigDecimal area,
        AreaUnit areaUnit,
        IrrigationType irrigationType,
        String currentCrop,
        String previousCrop,
        Season season,
        Location location,
        Soil soilProfile,
        boolean soilDataAvailable,
        Instant createdAt,
        Instant updatedAt) {

    public record Location(
            UUID id,
            BigDecimal latitude,
            BigDecimal longitude,
            String state,
            String district,
            String taluk,
            String addressLabel) {
    }

    public record Soil(
            UUID id,
            BigDecimal ph,
            BigDecimal electricalConductivity,
            BigDecimal organicCarbon,
            BigDecimal nitrogen,
            BigDecimal phosphorus,
            BigDecimal potassium,
            BigDecimal sulphur,
            BigDecimal zinc,
            BigDecimal iron,
            BigDecimal manganese,
            BigDecimal copper,
            BigDecimal boron,
            SoilDataSource source,
            SoilDataClassification dataClassification,
            LocalDate measuredAt,
            BigDecimal confidence) {
    }
}
