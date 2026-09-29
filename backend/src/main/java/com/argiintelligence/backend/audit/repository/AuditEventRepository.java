package com.argiintelligence.backend.audit.repository;

import com.argiintelligence.backend.audit.entity.AuditAction;
import com.argiintelligence.backend.audit.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /** Every filter is optional (null = not filtered). Newest first comes from the Pageable's sort. */
    @Query("""
            select e from AuditEvent e
            where (:action is null or e.action = :action)
              and (:actor is null or e.actorUserId = :actor)
              and (cast(:from as timestamp) is null or e.occurredAt >= :from)
              and (cast(:to as timestamp) is null or e.occurredAt < :to)
            """)
    Page<AuditEvent> search(AuditAction action, UUID actor, Instant from, Instant to, Pageable pageable);
}
