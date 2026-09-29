package com.argiintelligence.backend.user.repository;

import com.argiintelligence.backend.user.entity.Role;
import com.argiintelligence.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRoleAndEnabledTrue(Role role);

    boolean existsByRole(Role role);

    /** Admin user list; {@code role} and {@code q} (substring of email or name, case-insensitive) are optional. */
    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:q is null or lower(u.email) like concat('%', :q, '%')
                             or lower(u.fullName) like concat('%', :q, '%'))
            """)
    Page<User> search(Role role, String q, Pageable pageable);
}
