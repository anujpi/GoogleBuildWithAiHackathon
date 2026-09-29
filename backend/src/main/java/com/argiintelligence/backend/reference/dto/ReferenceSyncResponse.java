package com.argiintelligence.backend.reference.dto;

import com.argiintelligence.backend.reference.entity.ReferenceSync;

import java.time.Instant;
import java.util.UUID;

/** Result of one reference sync. {@code message} is the error code of a FAILED sync, null otherwise. */
public record ReferenceSyncResponse(UUID id, Instant syncedAt, ReferenceSync.Status status, String mlModelVersion,
                                    String message) {

    public static ReferenceSyncResponse from(ReferenceSync s) {
        return new ReferenceSyncResponse(s.getId(), s.getSyncedAt(), s.getStatus(), s.getMlModelVersion(),
                s.getMessage());
    }
}
