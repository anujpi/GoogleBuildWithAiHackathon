package com.argiintelligence.backend.reference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * FAO EcoCrop limits for one crop (MASTER_SPEC D12, V7). Every limit is nullable: null = not available
 * (blocker B2 until transcribed), never an estimate. Read-only for the application; values change by migration.
 */
@Entity
@Table(name = "crop_requirement")
@Getter
@NoArgsConstructor
public class CropRequirement {

    @Id
    private String cropId;

    private BigDecimal phAbsMin;
    private BigDecimal phOptMin;
    private BigDecimal phOptMax;
    private BigDecimal phAbsMax;

    @Column(name = "temp_abs_min_c")
    private BigDecimal tempAbsMinC;
    @Column(name = "temp_opt_min_c")
    private BigDecimal tempOptMinC;
    @Column(name = "temp_opt_max_c")
    private BigDecimal tempOptMaxC;
    @Column(name = "temp_abs_max_c")
    private BigDecimal tempAbsMaxC;

    private String source;
    private String sourceUrl;
    private LocalDate retrievedOn;

    /** All four pH limits are known. */
    public boolean hasPhLimits() {
        return phAbsMin != null && phOptMin != null && phOptMax != null && phAbsMax != null;
    }

    /** All four temperature limits are known. */
    public boolean hasTemperatureLimits() {
        return tempAbsMinC != null && tempOptMinC != null && tempOptMaxC != null && tempAbsMaxC != null;
    }
}
