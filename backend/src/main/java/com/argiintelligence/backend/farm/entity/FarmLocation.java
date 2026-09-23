package com.argiintelligence.backend.farm.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Latitude/longitude are the source of truth; the table's PostGIS {@code geog} column is derived from them. */
@Entity
@Table(name = "farm_location")
@Getter
@Setter
public class FarmLocation {

    @Id
    @GeneratedValue
    private UUID id;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String state;
    private String district;
    private String taluk;
    private String addressLabel;
}
