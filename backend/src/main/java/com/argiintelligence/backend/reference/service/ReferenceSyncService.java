package com.argiintelligence.backend.reference.service;

import com.argiintelligence.backend.audit.entity.AuditAction;
import com.argiintelligence.backend.audit.service.AuditService;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.dto.MlHealthResponse;
import com.argiintelligence.backend.ml.dto.ScopeResponse;
import com.argiintelligence.backend.reference.dto.ReferenceSyncResponse;
import com.argiintelligence.backend.reference.entity.RefCrop;
import com.argiintelligence.backend.reference.entity.RefDataset;
import com.argiintelligence.backend.reference.entity.RefDistrict;
import com.argiintelligence.backend.reference.entity.RefState;
import com.argiintelligence.backend.reference.entity.RefSupplySeries;
import com.argiintelligence.backend.reference.entity.ReferenceSync;
import com.argiintelligence.backend.reference.repository.RefCropRepository;
import com.argiintelligence.backend.reference.repository.RefDatasetRepository;
import com.argiintelligence.backend.reference.repository.RefDistrictRepository;
import com.argiintelligence.backend.reference.repository.RefStateRepository;
import com.argiintelligence.backend.reference.repository.RefSupplySeriesRepository;
import com.argiintelligence.backend.reference.repository.ReferenceSyncRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Mirrors the ML service's supported scope into the ref_* tables (MASTER_SPEC D3, §6.3). ML is the authority;
 * nothing here adds, filters or invents ids. States, districts and crops are upserted and never deleted (farms
 * reference them); supply series and datasets are replaced, so they always describe the currently served artifact.
 * A failed sync changes nothing but the sync history.
 */
@Slf4j
@Service
public class ReferenceSyncService {

    private final MlClient ml;
    private final RefStateRepository states;
    private final RefDistrictRepository districts;
    private final RefCropRepository crops;
    private final RefSupplySeriesRepository series;
    private final RefDatasetRepository datasets;
    private final ReferenceSyncRepository syncs;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public ReferenceSyncService(MlClient ml, RefStateRepository states, RefDistrictRepository districts,
                                RefCropRepository crops, RefSupplySeriesRepository series,
                                RefDatasetRepository datasets, ReferenceSyncRepository syncs, AuditService audit,
                                PlatformTransactionManager transactionManager) {
        this.ml = ml;
        this.states = states;
        this.districts = districts;
        this.crops = crops;
        this.series = series;
        this.datasets = datasets;
        this.syncs = syncs;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Runs one sync. Never throws for an ML problem: the outcome is the returned (and stored) status. */
    public ReferenceSyncResponse sync(UUID actorUserId) {
        ReferenceSync outcome;
        try {
            // HTTP happens outside any transaction; only the local write is transactional.
            ScopeResponse scope = ml.scope();
            String modelVersion = supplyModelVersion();
            outcome = tx.execute(status -> {
                apply(scope);
                return syncs.save(new ReferenceSync(ReferenceSync.Status.SUCCEEDED, modelVersion, null));
            });
            log.info("Reference scope synced: {} districts, {} crops, {} supply series",
                    scope.districts().size(), scope.crops().size(), scope.supplySeries().size());
        } catch (ApiException ex) {
            log.warn("Reference sync failed: {} ({})", ex.getCode(), ex.getMessage());
            outcome = syncs.save(new ReferenceSync(ReferenceSync.Status.FAILED, null, ex.getCode()));
        }
        audit.record(AuditAction.REFERENCE_SYNCED, actorUserId, "REFERENCE_SYNC", outcome.getId().toString(),
                outcome.getMessage() == null ? Map.of("status", outcome.getStatus().name())
                        : Map.of("status", outcome.getStatus().name(), "reason", outcome.getMessage()));
        return ReferenceSyncResponse.from(outcome);
    }

    private void apply(ScopeResponse scope) {
        scope.states().forEach(s -> states.save(new RefState(s.stateId(), s.label())));
        scope.districts().forEach(d -> districts.save(new RefDistrict(d.districtId(), d.stateId(), d.label())));
        scope.crops().forEach(c -> crops.save(new RefCrop(c.cropId(), c.label())));
        series.deleteAllInBatch();
        series.saveAll(scope.supplySeries().stream().map(s -> new RefSupplySeries(s.districtId(), s.cropId(),
                s.season(), s.firstYear(), s.lastYear(), s.yearsObserved())).toList());
        datasets.deleteAllInBatch();
        datasets.saveAll(scope.datasets().stream()
                .map(d -> new RefDataset(d.source(), d.datasetVersion(), d.dataThrough())).toList());
    }

    /** The READY supply model's version, or null if ML does not state one (never invented). */
    private String supplyModelVersion() {
        try {
            MlHealthResponse health = ml.health();
            return health.models().stream().filter(m -> "SUPPLY".equals(m.capability())).findFirst()
                    .map(MlHealthResponse.Model::modelVersion).orElse(null);
        } catch (ApiException ex) {
            return null;
        }
    }
}
