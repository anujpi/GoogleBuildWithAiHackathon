package com.argiintelligence.backend.reference.service;

import com.argiintelligence.backend.reference.dto.ReferenceScopeResponse;
import com.argiintelligence.backend.reference.entity.CropRequirement;
import com.argiintelligence.backend.reference.entity.RefCrop;
import com.argiintelligence.backend.reference.entity.RefDistrict;
import com.argiintelligence.backend.reference.entity.RefSupplySeries;
import com.argiintelligence.backend.reference.entity.ReferenceSync;
import com.argiintelligence.backend.reference.repository.CropRequirementRepository;
import com.argiintelligence.backend.reference.repository.RefCropRepository;
import com.argiintelligence.backend.reference.repository.RefDatasetRepository;
import com.argiintelligence.backend.reference.repository.RefDistrictRepository;
import com.argiintelligence.backend.reference.repository.RefStateRepository;
import com.argiintelligence.backend.reference.repository.RefSupplySeriesRepository;
import com.argiintelligence.backend.reference.repository.ReferenceSyncRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** Read access to the synced reference scope (MASTER_SPEC §6.3). Never proxies ML live. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferenceService {

    private final RefStateRepository states;
    private final RefDistrictRepository districts;
    private final RefCropRepository crops;
    private final RefSupplySeriesRepository series;
    private final RefDatasetRepository datasets;
    private final ReferenceSyncRepository syncs;
    private final CropRequirementRepository requirements;

    public ReferenceScopeResponse scope() {
        Optional<Instant> syncedAt = lastSuccessfulSync().map(ReferenceSync::getSyncedAt);
        if (syncedAt.isEmpty()) {
            // Nothing is served until ML's scope has been loaded, even the migration-seeded crops.
            return new ReferenceScopeResponse(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
        }
        List<RefSupplySeries> allSeries = series.findAll(Sort.by("districtId", "cropId", "season"));
        return new ReferenceScopeResponse(
                states.findAll(Sort.by("stateId")).stream()
                        .map(s -> new ReferenceScopeResponse.State(s.getStateId(), s.getLabel())).toList(),
                districts.findAll(Sort.by("districtId")).stream()
                        .map(d -> new ReferenceScopeResponse.District(d.getDistrictId(), d.getStateId(), d.getLabel()))
                        .toList(),
                crops.findAll(Sort.by("cropId")).stream()
                        .map(c -> new ReferenceScopeResponse.Crop(c.getCropId(), c.getLabel())).toList(),
                allSeries.stream().map(RefSupplySeries::getSeason).distinct().sorted().toList(),
                allSeries.stream().map(s -> new ReferenceScopeResponse.SupplySeries(s.getDistrictId(), s.getCropId(),
                        s.getSeason(), s.getFirstYear(), s.getLastYear(), s.getYearsObserved(),
                        IntStream.rangeClosed(s.getFirstYear() + 1, s.getLastYear() + 1).boxed().toList())).toList(),
                datasets.findAll(Sort.by("source", "datasetVersion")).stream()
                        .map(d -> new ReferenceScopeResponse.Dataset(d.getSource(), d.getDatasetVersion(),
                                d.getDataThrough())).toList(),
                syncedAt.get());
    }

    public Optional<ReferenceSync> lastSync() {
        return syncs.findFirstByOrderBySyncedAtDesc();
    }

    public Optional<ReferenceSync> lastSuccessfulSync() {
        return syncs.findFirstByStatusOrderBySyncedAtDesc(ReferenceSync.Status.SUCCEEDED);
    }

    public Optional<RefDistrict> district(String districtId) {
        return districts.findById(districtId);
    }

    public Optional<RefCrop> crop(String cropId) {
        return crops.findById(cropId);
    }

    /** The D2 crops (seeded by V7, upserted by the sync), by id. Candidates without a series become NOT_SUPPORTED. */
    public List<RefCrop> allCrops() {
        return crops.findAll(Sort.by("cropId"));
    }

    public Optional<RefSupplySeries> series(String districtId, String cropId, String season) {
        return series.findById(new RefSupplySeries.Key(districtId, cropId, season));
    }

    /** "In scope" = the district has at least one supply series in the served artifact. */
    public boolean districtInScope(String districtId) {
        return districtId != null && series.existsByDistrictId(districtId);
    }

    public Optional<CropRequirement> requirement(String cropId) {
        return requirements.findById(cropId);
    }
}
