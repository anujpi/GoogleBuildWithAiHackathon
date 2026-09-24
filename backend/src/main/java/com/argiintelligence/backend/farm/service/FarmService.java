package com.argiintelligence.backend.farm.service;

import com.argiintelligence.backend.farm.dto.FarmRequest;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.entity.Farm;
import com.argiintelligence.backend.farm.exception.FarmNotFoundException;
import com.argiintelligence.backend.farm.exception.InconsistentSoilProvenanceException;
import com.argiintelligence.backend.farm.mapper.FarmMapper;
import com.argiintelligence.backend.farm.repository.FarmRepository;
import com.argiintelligence.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmRepository farms;
    private final UserRepository users;

    @Transactional
    public FarmResponse create(UUID ownerId, FarmRequest request) {
        checkSoilProvenance(request);
        Farm farm = new Farm();
        farm.setOwner(users.getReferenceById(ownerId));
        FarmMapper.apply(request, farm);
        return FarmMapper.toResponse(farms.save(farm));
    }

    // ponytail: unpaged list, fine for MVP farm counts; switch to Pageable before it can grow large.
    @Transactional(readOnly = true)
    public List<FarmResponse> list(UUID ownerId) {
        return farms.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(FarmMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FarmResponse get(UUID ownerId, UUID id) {
        return FarmMapper.toResponse(find(ownerId, id));
    }

    @Transactional
    public FarmResponse update(UUID ownerId, UUID id, FarmRequest request) {
        checkSoilProvenance(request);
        Farm farm = find(ownerId, id);
        FarmMapper.apply(request, farm);
        farm.touch();
        farms.flush(); // assigns the id of a soil profile newly added on this update
        return FarmMapper.toResponse(farm);
    }

    private static void checkSoilProvenance(FarmRequest request) {
        FarmRequest.Soil soil = request.soilProfile();
        if (soil != null && !soil.source().permits(soil.dataClassification())) {
            throw new InconsistentSoilProvenanceException(soil.source(), soil.dataClassification());
        }
    }

    /** Another user's farm is reported exactly like a missing one (404), so its existence is not revealed. */
    private Farm find(UUID ownerId, UUID id) {
        return farms.findWithDetailsByIdAndOwnerId(id, ownerId).orElseThrow(() -> new FarmNotFoundException(id));
    }
}
