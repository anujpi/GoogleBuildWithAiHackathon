package com.argiintelligence.backend.reference.repository;

import com.argiintelligence.backend.reference.entity.RefSupplySeries;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefSupplySeriesRepository extends JpaRepository<RefSupplySeries, RefSupplySeries.Key> {

    List<RefSupplySeries> findByDistrictId(String districtId);

    boolean existsByDistrictId(String districtId);
}
