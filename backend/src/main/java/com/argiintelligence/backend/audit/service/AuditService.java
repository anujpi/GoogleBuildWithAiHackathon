package com.argiintelligence.backend.audit.service;

import com.argiintelligence.backend.audit.dto.AuditEventResponse;
import com.argiintelligence.backend.audit.entity.AuditAction;
import com.argiintelligence.backend.audit.entity.AuditEvent;
import com.argiintelligence.backend.audit.repository.AuditEventRepository;
import com.argiintelligence.backend.common.api.PageResponse;
import com.argiintelligence.backend.common.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository events;
    private final JsonMapper json;

    /**
     * Records one event in the caller's transaction, so the change and its audit row commit (or roll back)
     * together, and the row can reference rows the caller has just written (e.g. a newly registered user).
     * Callers must never pass passwords, tokens or secrets.
     */
    @Transactional
    public void record(AuditAction action, UUID actorUserId, String targetType, String targetId,
                       Map<String, ?> details) {
        save(action, actorUserId, targetType, targetId, details);
    }

    /**
     * Records one event in its own transaction: for events whose caller is read-only or about to fail, such as
     * a login attempt, which must still be recorded. The actor (if any) must already be committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditAction action, UUID actorUserId, String targetType, String targetId,
                                    Map<String, ?> details) {
        save(action, actorUserId, targetType, targetId, details);
    }

    private void save(AuditAction action, UUID actorUserId, String targetType, String targetId, Map<String, ?> details) {
        events.save(new AuditEvent(actorUserId, action, targetType, targetId, json.writeValueAsString(details),
                RequestIdFilter.current()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(AuditAction action, UUID actorUserId, Instant from, Instant to,
                                                   int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return PageResponse.of(events.search(action, actorUserId, from, to, pageable),
                e -> AuditEventResponse.from(e, json));
    }
}
