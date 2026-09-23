package com.argiintelligence.backend.farm.service;

import com.argiintelligence.backend.farm.dto.FarmRequest;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.entity.Farm;
import com.argiintelligence.backend.farm.exception.FarmNotFoundException;
import com.argiintelligence.backend.farm.exception.InconsistentSoilProvenanceException;
import com.argiintelligence.backend.farm.mapper.FarmMapper;
import com.argiintelligence.backend.farm.repository.FarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmRepository farms;

    @Transactional
    public FarmResponse create(FarmRequest request) {
        checkSoilProvenance(request);
        Farm farm = new Farm();
        FarmMapper.apply(request, farm);
        return FarmMapper.toResponse(farms.save(farm));
    }

    // ponytail: unpaged list, fine for MVP farm counts; switch to Pageable before it can grow large.
    @Transactional(readOnly = true)
    public List<FarmResponse> list() {
        return farms.findAllByOrderByCreatedAtDesc().stream().map(FarmMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FarmResponse get(UUID id) {
        return FarmMapper.toResponse(find(id));
    }

    @Transactional
    public FarmResponse update(UUID id, FarmRequest request) {
        checkSoilProvenance(request);
        Farm farm = find(id);
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

    private Farm find(UUID id) {
        return farms.findWithDetailsById(id).orElseThrow(() -> new FarmNotFoundException(id));
    }
}
