package com.argiintelligence.backend.reference.repository;

import com.argiintelligence.backend.reference.entity.RefDataset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefDatasetRepository extends JpaRepository<RefDataset, RefDataset.Key> {
}
