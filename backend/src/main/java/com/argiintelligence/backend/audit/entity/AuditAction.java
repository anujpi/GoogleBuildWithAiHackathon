package com.argiintelligence.backend.audit.entity;

/** The audited actions of MASTER_SPEC §5.1 V6 (mirrored by the table's CHECK constraint). */
public enum AuditAction {
    USER_REGISTERED,
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    ROLE_CHANGED,
    USER_ENABLED,
    USER_DISABLED,
    DISTRICTS_ASSIGNED,
    FARM_CREATED,
    FARM_UPDATED,
    FARM_OWNER_ASSIGNED,
    REFERENCE_SYNCED,
    ADMIN_BOOTSTRAPPED
}
