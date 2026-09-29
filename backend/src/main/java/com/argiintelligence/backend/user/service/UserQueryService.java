package com.argiintelligence.backend.user.service;

import com.argiintelligence.backend.user.dto.UserResponse;
import com.argiintelligence.backend.user.entity.User;
import com.argiintelligence.backend.user.entity.UserDistrictAssignment;
import com.argiintelligence.backend.user.repository.UserDistrictAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserDistrictAssignmentRepository assignments;

    public UserResponse toResponse(User user) {
        return UserResponse.from(user, assignedDistrictIds(user.getId()));
    }

    public List<String> assignedDistrictIds(UUID userId) {
        return assignments.findByUserIdOrderByDistrictId(userId).stream()
                .map(UserDistrictAssignment::getDistrictId).toList();
    }
}
