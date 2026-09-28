package com.argiintelligence.backend.supply.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One state x crop x season x crop year of published area and production. Read-only: rows are loaded by
 * Flyway migrations from real datasets and never written by the application.
 */
@Entity
@Table(name = "production_history")
@Getter
public class ProductionHistory {

    @Id
    private UUID id;

    private String state;
    private String crop;
    /** The source's own season label, e.g. "Kharif", "Rabi", "Whole Year". */
    private String season;
    private int cropYear;
    private BigDecimal areaHectares;
    private BigDecimal productionTonnes;

    @Enumerated(EnumType.STRING)
    private ProductionDataSource source;

    @Enumerated(EnumType.STRING)
    private ProductionDataClassification dataClassification;

    private String datasetVersion;

    protected ProductionHistory() {
    }

    /** For tests and fixtures only; production rows come from migrations. */
    public ProductionHistory(String state, String crop, String season, int cropYear, BigDecimal areaHectares,
                             BigDecimal productionTonnes, ProductionDataSource source,
                             ProductionDataClassification dataClassification, String datasetVersion) {
        this.id = UUID.randomUUID();
        this.state = state;
        this.crop = crop;
        this.season = season;
        this.cropYear = cropYear;
        this.areaHectares = areaHectares;
        this.productionTonnes = productionTonnes;
        this.source = source;
        this.dataClassification = dataClassification;
        this.datasetVersion = datasetVersion;
    }
}
