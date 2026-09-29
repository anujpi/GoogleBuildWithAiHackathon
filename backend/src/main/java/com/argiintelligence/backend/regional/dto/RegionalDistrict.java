package com.argiintelligence.backend.regional.dto;

/** A district visible to the caller, with the number of (owned) farms in it. */
public record RegionalDistrict(String districtId, String stateId, String label, long farmCount) {
}
