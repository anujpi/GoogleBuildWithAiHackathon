package com.argiintelligence.backend.supply.repository;

import com.argiintelligence.backend.supply.entity.ProductionHistory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Read-only access. State and crop match case-insensitively; the season label must match exactly. */
public interface ProductionHistoryRepository extends Repository<ProductionHistory, UUID> {

    @Query("""
            select p from ProductionHistory p
            where lower(p.state) = lower(:state) and lower(p.crop) = lower(:crop) and p.season = :season
              and p.cropYear between :fromYear and :toYear
            order by p.cropYear desc""")
    List<ProductionHistory> findSeries(@Param("state") String state, @Param("crop") String crop,
                                       @Param("season") String season, @Param("fromYear") int fromYear,
                                       @Param("toYear") int toYear);

    /** All years recorded for a series, oldest first; used to explain why history is insufficient. */
    @Query("""
            select p.cropYear from ProductionHistory p
            where lower(p.state) = lower(:state) and lower(p.crop) = lower(:crop) and p.season = :season
            order by p.cropYear""")
    List<Integer> findSeriesYears(@Param("state") String state, @Param("crop") String crop,
                                  @Param("season") String season);
}
