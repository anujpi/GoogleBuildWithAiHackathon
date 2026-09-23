package com.argiintelligence.backend.farm.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Aggregate root: location and soil profile belong to exactly one farm and are only created through it,
 * so persist cascades to them. Updates mutate them in place; only a removed soil profile is deleted.
 */
@Entity
@Table(name = "farm")
@Getter
@Setter
public class Farm {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;
    private BigDecimal area;

    @Enumerated(EnumType.STRING)
    private AreaUnit areaUnit;

    @Enumerated(EnumType.STRING)
    private IrrigationType irrigationType;

    private String currentCrop;
    private String previousCrop;

    @Enumerated(EnumType.STRING)
    private Season season;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "location_id")
    private FarmLocation location;

    // Optional: null means soil data is unavailable. Clearing it on update deletes the old profile row.
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST, orphanRemoval = true)
    @JoinColumn(name = "soil_profile_id")
    private SoilProfile soilProfile;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = now();
        updatedAt = createdAt;
    }

    /** Called on every update; a change to only the location or soil profile still counts as a farm update. */
    public void touch() {
        updatedAt = now();
    }

    // Postgres stores microseconds; truncating keeps create/update responses identical to later reads.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
