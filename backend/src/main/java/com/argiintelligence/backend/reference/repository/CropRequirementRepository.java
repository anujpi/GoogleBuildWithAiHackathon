package com.argiintelligence.backend.reference.repository;

import com.argiintelligence.backend.reference.entity.CropRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropRequirementRepository extends JpaRepository<CropRequirement, String> {
}
