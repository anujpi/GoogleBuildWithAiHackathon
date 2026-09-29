package com.argiintelligence.backend.farm.repository;

import com.argiintelligence.backend.farm.entity.Farm;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every lookup is scoped: to an owner, to owned farms in a district, or to ownerless legacy farms. There is no
 * unscoped lookup by id, so a query can never leak a farm the caller may not see.
 * Location and soil are fetched with the farm (no N+1).
 */
public interface FarmRepository extends JpaRepository<Farm, UUID> {

    @EntityGraph(attributePaths = {"location", "soilProfile"})
    Optional<Farm> findWithDetailsByIdAndOwnerId(UUID id, UUID ownerId);

    @EntityGraph(attributePaths = {"location", "soilProfile"})
    List<Farm> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    /** Owned farm with a district (regional view); legacy ownerless farms are never regional. */
    @EntityGraph(attributePaths = {"location", "soilProfile"})
    Optional<Farm> findWithDetailsByIdAndOwnerIsNotNullAndDistrictIdIsNotNull(UUID id);

    @EntityGraph(attributePaths = {"soilProfile"})
    List<Farm> findAllByDistrictIdAndOwnerIsNotNullOrderByCreatedAtDesc(String districtId);

    long countByDistrictIdAndOwnerIsNotNull(String districtId);

    @EntityGraph(attributePaths = {"soilProfile"})
    List<Farm> findAllByOwnerIsNullOrderByCreatedAtDesc();

    Optional<Farm> findByIdAndOwnerIsNull(UUID id);
}
