package com.argiintelligence.backend.farm.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Every measurement is nullable: a missing value stays null and is never filled in. */
@Entity
@Table(name = "soil_profile")
@Getter
@Setter
public class SoilProfile {

    @Id
    @GeneratedValue
    private UUID id;

    private BigDecimal ph;
    private BigDecimal electricalConductivity;
    private BigDecimal organicCarbon;
    private BigDecimal nitrogen;
    private BigDecimal phosphorus;
    private BigDecimal potassium;
    private BigDecimal sulphur;
    private BigDecimal zinc;
    private BigDecimal iron;
    private BigDecimal manganese;
    private BigDecimal copper;
    private BigDecimal boron;

    @Enumerated(EnumType.STRING)
    private SoilDataSource source;

    @Enumerated(EnumType.STRING)
    private SoilDataClassification dataClassification;

    private LocalDate measuredAt;
    private BigDecimal confidence;
}
