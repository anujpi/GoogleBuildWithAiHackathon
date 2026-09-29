package com.argiintelligence.backend.audit.dto;

import com.argiintelligence.backend.audit.entity.AuditAction;
import com.argiintelligence.backend.audit.entity.AuditEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(UUID id, Instant occurredAt, UUID actorUserId, AuditAction action,
                                 String targetType, String targetId, JsonNode details, String requestId) {

    public static AuditEventResponse from(AuditEvent e, JsonMapper json) {
        return new AuditEventResponse(e.getId(), e.getOccurredAt(), e.getActorUserId(), e.getAction(),
                e.getTargetType(), e.getTargetId(), json.readTree(e.getDetails()), e.getRequestId());
    }
}
