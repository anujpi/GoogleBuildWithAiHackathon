package com.argiintelligence.backend.reference.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** REFERENCE_SYNC_ON_STARTUP (MASTER_SPEC §14): a failure is logged and the app still starts. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reference.sync-on-startup", havingValue = "true")
class ReferenceSyncOnStartup implements ApplicationRunner {

    private final ReferenceSyncService sync;

    @Override
    public void run(ApplicationArguments args) {
        try {
            sync.sync(null);
        } catch (RuntimeException ex) {
            log.error("Reference sync on startup failed; the application continues without it", ex);
        }
    }
}
