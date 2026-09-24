package com.argiintelligence.backend.user.entity;

/**
 * FARMER and FPO manage their own farms. ADMIN and AGRICULTURAL_OFFICER exist, but their broader
 * permissions are not implemented yet. Public registration always assigns FARMER.
 */
public enum Role {
    FARMER,
    FPO,
    AGRICULTURAL_OFFICER,
    ADMIN
}
