package com.argiintelligence.backend.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** A district an ADMIN assigned to an FPO or AGRICULTURAL_OFFICER user (MASTER_SPEC D9, V5). */
@Entity
@Table(name = "user_district_assignment")
@IdClass(UserDistrictAssignment.Key.class)
@Getter
@NoArgsConstructor
public class UserDistrictAssignment {

    @Id
    private UUID userId;
    @Id
    private String districtId;
    private Instant assignedAt;
    private UUID assignedBy;

    public UserDistrictAssignment(UUID userId, String districtId, UUID assignedBy) {
        this.userId = userId;
        this.districtId = districtId;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID userId;
        private String districtId;
    }
}
