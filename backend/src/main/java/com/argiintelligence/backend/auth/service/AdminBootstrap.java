package com.argiintelligence.backend.auth.service;

import com.argiintelligence.backend.audit.entity.AuditAction;
import com.argiintelligence.backend.audit.service.AuditService;
import com.argiintelligence.backend.user.entity.Role;
import com.argiintelligence.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * BOOTSTRAP_ADMIN_EMAIL (MASTER_SPEC §14): if set and no ADMIN exists yet, the already-registered user with this
 * email becomes ADMIN at startup. No account or password is ever created from configuration.
 */
@Slf4j
@Component
class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final AuditService audit;
    private final String email;

    AdminBootstrap(UserRepository users, AuditService audit, @Value("${app.bootstrap.admin-email:}") String email) {
        this.users = users;
        this.audit = audit;
        this.email = email;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || users.existsByRole(Role.ADMIN)) {
            return;
        }
        users.findByEmail(AuthService.normalize(email)).ifPresentOrElse(user -> {
            Role previous = user.getRole();
            user.setRole(Role.ADMIN);
            audit.record(AuditAction.ADMIN_BOOTSTRAPPED, null, "USER", user.getId().toString(),
                    Map.of("previousRole", previous.name()));
            log.info("Bootstrapped the first ADMIN from BOOTSTRAP_ADMIN_EMAIL");
        }, () -> log.warn("BOOTSTRAP_ADMIN_EMAIL is set but no registered user has that email; no ADMIN created"));
    }
}
