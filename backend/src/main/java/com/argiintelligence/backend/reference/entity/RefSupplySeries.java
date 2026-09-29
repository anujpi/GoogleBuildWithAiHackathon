package com.argiintelligence.backend.reference.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** One reported district x crop x season production series in the served ML artifact. */
@Entity
@Table(name = "ref_supply_series")
@IdClass(RefSupplySeries.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefSupplySeries {

    @Id
    private String districtId;
    @Id
    private String cropId;
    /** Canonical ML season (KHARIF, RABI, SUMMER, WHOLE_YEAR, AUTUMN, WINTER). */
    @Id
    private String season;
    private int firstYear;
    private int lastYear;
    private int yearsObserved;

    /** Candidate crop years (MASTER_SPEC §6.3): each needs at least a t-1 reported year. */
    public boolean isEstimable(int cropYear) {
        return cropYear >= firstYear + 1 && cropYear <= lastYear + 1;
    }

    public int latestEstimableYear() {
        return lastYear + 1;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private String districtId;
        private String cropId;
        private String season;
    }
}
