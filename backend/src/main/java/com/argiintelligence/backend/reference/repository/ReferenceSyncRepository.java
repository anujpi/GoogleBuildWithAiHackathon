package com.argiintelligence.backend.reference.repository;

import com.argiintelligence.backend.reference.entity.ReferenceSync;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReferenceSyncRepository extends JpaRepository<ReferenceSync, UUID> {

    Optional<ReferenceSync> findFirstByOrderBySyncedAtDesc();

    Optional<ReferenceSync> findFirstByStatusOrderBySyncedAtDesc(ReferenceSync.Status status);
}
