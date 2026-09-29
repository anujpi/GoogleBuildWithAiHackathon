package com.argiintelligence.backend.farm.service;

import com.argiintelligence.backend.audit.entity.AuditAction;
import com.argiintelligence.backend.audit.service.AuditService;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.dto.FarmRequest;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.dto.FarmSummary;
import com.argiintelligence.backend.farm.entity.Farm;
import com.argiintelligence.backend.farm.exception.FarmNotFoundException;
import com.argiintelligence.backend.farm.exception.InconsistentSoilProvenanceException;
import com.argiintelligence.backend.farm.mapper.FarmMapper;
import com.argiintelligence.backend.farm.repository.FarmRepository;
import com.argiintelligence.backend.reference.entity.RefDistrict;
import com.argiintelligence.backend.reference.service.ReferenceService;
import com.argiintelligence.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmRepository farms;
    private final UserRepository users;
    private final ReferenceService reference;
    private final AuditService audit;

    @Transactional
    public FarmResponse create(UUID ownerId, FarmRequest request) {
        validate(request);
        Farm farm = new Farm();
        farm.setOwner(users.getReferenceById(ownerId));
        FarmMapper.apply(request, farm);
        farms.save(farm);
        audit.record(AuditAction.FARM_CREATED, ownerId, "FARM", farm.getId().toString(), Map.of());
        return toResponse(farm);
    }

    // ponytail: unpaged list, fine for MVP farm counts; switch to Pageable before it can grow large.
    @Transactional(readOnly = true)
    public List<FarmResponse> list(UUID ownerId) {
        return farms.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FarmResponse get(UUID ownerId, UUID id) {
        return toResponse(owned(ownerId, id));
    }

    @Transactional
    public FarmResponse update(UUID ownerId, UUID id, FarmRequest request) {
        validate(request);
        Farm farm = owned(ownerId, id);
        FarmMapper.apply(request, farm);
        farm.touch();
        farms.flush(); // assigns the id of a soil profile newly added on this update
        audit.record(AuditAction.FARM_UPDATED, ownerId, "FARM", id.toString(), Map.of());
        return toResponse(farm);
    }

    /**
     * A farm visible in the regional view: owned and in a district. Whether the caller may see that district is
     * decided by the regional module; this only guarantees legacy ownerless farms are never returned.
     */
    @Transactional(readOnly = true)
    public FarmResponse regionalFarm(UUID id) {
        return toResponse(farms.findWithDetailsByIdAndOwnerIsNotNullAndDistrictIdIsNotNull(id)
                .orElseThrow(() -> new FarmNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public List<FarmSummary> summariesInDistrict(String districtId) {
        String label = reference.district(districtId).map(RefDistrict::getLabel).orElse(null);
        return farms.findAllByDistrictIdAndOwnerIsNotNullOrderByCreatedAtDesc(districtId).stream()
                .map(f -> FarmMapper.toSummary(f, label)).toList();
    }

    @Transactional(readOnly = true)
    public long countInDistrict(String districtId) {
        return farms.countByDistrictIdAndOwnerIsNotNull(districtId);
    }

    /** Legacy farms created before accounts existed (MASTER_SPEC D13). Admin only. */
    @Transactional(readOnly = true)
    public List<FarmSummary> unownedSummaries() {
        return farms.findAllByOwnerIsNullOrderByCreatedAtDesc().stream()
                .map(f -> FarmMapper.toSummary(f, districtLabel(f.getDistrictId()))).toList();
    }

    /** Assigns an owner to an ownerless farm only; the caller has already checked the target user's role. */
    @Transactional
    public FarmSummary assignOwner(UUID farmId, UUID ownerId, UUID adminId) {
        Farm farm = farms.findById(farmId).orElseThrow(() -> new FarmNotFoundException(farmId));
        if (farm.getOwner() != null) {
            throw ApiException.conflict("This farm already has an owner");
        }
        farm.setOwner(users.getReferenceById(ownerId));
        farm.touch();
        audit.record(AuditAction.FARM_OWNER_ASSIGNED, adminId, "FARM", farmId.toString(),
                Map.of("ownerUserId", ownerId.toString()));
        return FarmMapper.toSummary(farm, districtLabel(farm.getDistrictId()));
    }

    private FarmResponse toResponse(Farm farm) {
        String districtId = farm.getDistrictId();
        return FarmMapper.toResponse(farm, districtLabel(districtId), reference.districtInScope(districtId));
    }

    private String districtLabel(String districtId) {
        return districtId == null ? null : reference.district(districtId).map(RefDistrict::getLabel).orElse(null);
    }

    private void validate(FarmRequest request) {
        FarmRequest.Soil soil = request.soilProfile();
        if (soil != null && !soil.source().permits(soil.dataClassification())) {
            throw new InconsistentSoilProvenanceException(soil.source(), soil.dataClassification());
        }
        if (request.districtId() != null && reference.district(request.districtId()).isEmpty()) {
            throw ApiException.validation("districtId", "is not a supported district");
        }
    }

    /** Another user's farm is reported exactly like a missing one (404), so its existence is not revealed. */
    private Farm owned(UUID ownerId, UUID id) {
        return farms.findWithDetailsByIdAndOwnerId(id, ownerId).orElseThrow(() -> new FarmNotFoundException(id));
    }
}
