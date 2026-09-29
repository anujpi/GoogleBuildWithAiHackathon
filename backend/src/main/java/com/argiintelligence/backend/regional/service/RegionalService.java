package com.argiintelligence.backend.regional.service;

import com.argiintelligence.backend.auth.security.AuthenticatedUser;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.dto.FarmSummary;
import com.argiintelligence.backend.farm.exception.FarmNotFoundException;
import com.argiintelligence.backend.farm.service.FarmService;
import com.argiintelligence.backend.reference.entity.RefDistrict;
import com.argiintelligence.backend.reference.repository.RefDistrictRepository;
import com.argiintelligence.backend.regional.dto.RegionalDistrict;
import com.argiintelligence.backend.user.entity.Role;
import com.argiintelligence.backend.user.repository.UserDistrictAssignmentRepository;
import com.argiintelligence.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Read-only regional view (MASTER_SPEC §6.6, D9). ADMIN sees every district; FPO and AGRICULTURAL_OFFICER see only
 * the districts an ADMIN assigned to them. Anything outside that is reported as missing (404), never as forbidden,
 * so a caller cannot probe which farms or districts exist. The role gate itself is in SecurityConfig.
 */
@Service
@RequiredArgsConstructor
public class RegionalService {

    private final RefDistrictRepository districts;
    private final UserDistrictAssignmentRepository assignments;
    private final UserQueryService userQueries;
    private final FarmService farmService;

    public List<RegionalDistrict> districts(AuthenticatedUser user) {
        List<RefDistrict> visible = user.role() == Role.ADMIN
                ? districts.findAll(Sort.by("districtId"))
                : districts.findAllById(userQueries.assignedDistrictIds(user.id())).stream()
                        .sorted((a, b) -> a.getDistrictId().compareTo(b.getDistrictId())).toList();
        return visible.stream().map(d -> new RegionalDistrict(d.getDistrictId(), d.getStateId(), d.getLabel(),
                farmService.countInDistrict(d.getDistrictId()))).toList();
    }

    public List<FarmSummary> farms(AuthenticatedUser user, String districtId) {
        requireVisibleDistrict(user, districtId);
        return farmService.summariesInDistrict(districtId);
    }

    /** A farm in one of the caller's districts; any other farm, including legacy ownerless ones, is a 404. */
    public FarmResponse farm(AuthenticatedUser user, UUID farmId) {
        FarmResponse farm = farmService.regionalFarm(farmId);
        if (!canSee(user, farm.districtId())) {
            throw new FarmNotFoundException(farmId);
        }
        return farm;
    }

    private void requireVisibleDistrict(AuthenticatedUser user, String districtId) {
        if (!districts.existsById(districtId) || !canSee(user, districtId)) {
            throw ApiException.notFound("DISTRICT_NOT_FOUND", "District " + districtId + " not found");
        }
    }

    private boolean canSee(AuthenticatedUser user, String districtId) {
        return user.role() == Role.ADMIN || assignments.existsByUserIdAndDistrictId(user.id(), districtId);
    }
}
