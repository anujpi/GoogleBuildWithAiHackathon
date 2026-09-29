package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.api.RiskLevel;
import com.argiintelligence.backend.intelligence.dto.CropEvidenceResponse.Tier;
import com.argiintelligence.backend.intelligence.service.RiskRules.PhFit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** crop-evidence-v1 tiers (MASTER_SPEC §10.2). */
class CropEvidenceTierTest {

    @Test
    void noSeriesIsNotSupportedWhateverElseHolds() {
        assertThat(CropEvidenceService.tier(false, PhFit.OPTIMAL, "WITHIN_OPTIMAL", RiskLevel.LOW))
                .isEqualTo(Tier.NOT_SUPPORTED);
    }

    @Test
    void soilOutsideAbsoluteOrCriticalRiskIsUnsuitable() {
        assertThat(CropEvidenceService.tier(true, PhFit.OUTSIDE_ABSOLUTE_RANGE, "WITHIN_OPTIMAL", RiskLevel.LOW))
                .isEqualTo(Tier.UNSUITABLE);
        assertThat(CropEvidenceService.tier(true, PhFit.OPTIMAL, "WITHIN_OPTIMAL", RiskLevel.CRITICAL))
                .isEqualTo(Tier.UNSUITABLE);
    }

    @Test
    void weatherOutsideItsAbsoluteRangeIsACautionNotUnsuitable() {
        assertThat(CropEvidenceService.tier(true, PhFit.OPTIMAL, "OUTSIDE_ABSOLUTE", RiskLevel.HIGH))
                .isEqualTo(Tier.SUITABLE_WITH_CAUTION);
    }

    @Test
    void tolerableEvidenceOrHighRiskIsACaution() {
        assertThat(CropEvidenceService.tier(true, PhFit.TOLERABLE, "WITHIN_OPTIMAL", RiskLevel.LOW))
                .isEqualTo(Tier.SUITABLE_WITH_CAUTION);
        assertThat(CropEvidenceService.tier(true, PhFit.OPTIMAL, "WITHIN_ABSOLUTE", RiskLevel.MODERATE))
                .isEqualTo(Tier.SUITABLE_WITH_CAUTION);
        assertThat(CropEvidenceService.tier(true, PhFit.OPTIMAL, "WITHIN_OPTIMAL", RiskLevel.HIGH))
                .isEqualTo(Tier.SUITABLE_WITH_CAUTION);
    }

    @Test
    void otherwiseSuitableEvenWhenEvidenceIsUnavailable() {
        assertThat(CropEvidenceService.tier(true, PhFit.OPTIMAL, "WITHIN_OPTIMAL", RiskLevel.MODERATE))
                .isEqualTo(Tier.SUITABLE);
        assertThat(CropEvidenceService.tier(true, PhFit.UNAVAILABLE, "UNAVAILABLE", RiskLevel.UNAVAILABLE))
                .isEqualTo(Tier.SUITABLE);
    }
}
