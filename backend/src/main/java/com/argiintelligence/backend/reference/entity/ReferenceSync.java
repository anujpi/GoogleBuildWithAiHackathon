package com.argiintelligence.backend.reference.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** One reference sync attempt (history row). */
@Entity
@Table(name = "reference_sync")
@Getter
@NoArgsConstructor
public class ReferenceSync {

    public enum Status { SUCCEEDED, FAILED }

    @Id
    @GeneratedValue
    private UUID id;

    private Instant syncedAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    /** The served supply model when known; null otherwise (never invented). */
    private String mlModelVersion;

    /** Why a sync failed (an error code, never a stack trace); null on success. */
    private String message;

    public ReferenceSync(Status status, String mlModelVersion, String message) {
        this.syncedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.status = status;
        this.mlModelVersion = mlModelVersion;
        this.message = message;
    }
}
