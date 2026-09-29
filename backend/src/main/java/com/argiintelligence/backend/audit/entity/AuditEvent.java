package com.argiintelligence.backend.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Append-only audit row. {@code details} is a JSON object that never holds passwords, tokens or secrets. */
@Entity
@Table(name = "audit_event")
@Getter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue
    private UUID id;

    private Instant occurredAt;

    /** Null for anonymous actions (failed login, startup jobs). */
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    private AuditAction action;

    private String targetType;
    private String targetId;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String details;

    private String requestId;

    public AuditEvent(UUID actorUserId, AuditAction action, String targetType, String targetId, String details,
                      String requestId) {
        this.occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
        this.requestId = requestId;
    }
}
