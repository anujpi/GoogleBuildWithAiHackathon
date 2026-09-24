package com.argiintelligence.backend.farm.repository;

import com.argiintelligence.backend.farm.entity.Farm;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FarmRepository extends JpaRepository<Farm, UUID> {

    // Every lookup is scoped to the owner, so another user's farm is indistinguishable from a missing one.
    // Location and soil are always returned with the farm, so fetch them in the same query (no N+1).
    @EntityGraph(attributePaths = {"location", "soilProfile"})
    Optional<Farm> findWithDetailsByIdAndOwnerId(UUID id, UUID ownerId);

    @EntityGraph(attributePaths = {"location", "soilProfile"})
    List<Farm> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
