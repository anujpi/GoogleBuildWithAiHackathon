package com.argiintelligence.backend.user.repository;

import com.argiintelligence.backend.user.entity.UserDistrictAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UserDistrictAssignmentRepository
        extends JpaRepository<UserDistrictAssignment, UserDistrictAssignment.Key> {

    List<UserDistrictAssignment> findByUserIdOrderByDistrictId(UUID userId);

    boolean existsByUserIdAndDistrictId(UUID userId, String districtId);

    @Modifying
    @Query("delete from UserDistrictAssignment a where a.userId = :userId")
    void deleteByUserId(UUID userId);
}
