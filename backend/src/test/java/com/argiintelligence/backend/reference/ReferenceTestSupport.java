package com.argiintelligence.backend.reference;

import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.dto.MlHealthResponse;
import com.argiintelligence.backend.ml.dto.ScopeResponse;
import com.argiintelligence.backend.reference.service.ReferenceSyncService;

import java.util.List;

import static org.mockito.Mockito.when;

/**
 * Loads reference data the way production does: through ReferenceSyncService from an (mocked) ML scope.
 * The ids follow the D3 format; the series years mirror the S01 coverage (1997-2014). Test fixtures only.
 */
public final class ReferenceTestSupport {

    public static final ScopeResponse SCOPE = new ScopeResponse(
            List.of(new ScopeResponse.State("up", "Uttar Pradesh")),
            List.of(new ScopeResponse.District("up-agra", "up", "Agra"),
                    new ScopeResponse.District("up-lucknow", "up", "Lucknow")),
            List.of(new ScopeResponse.Crop("potato", "Potato"), new ScopeResponse.Crop("wheat", "Wheat"),
                    new ScopeResponse.Crop("onion", "Onion"), new ScopeResponse.Crop("maize", "Maize")),
            List.of("KHARIF", "RABI"),
            List.of(new ScopeResponse.SupplySeries("up-agra", "potato", "RABI", 1997, 2014, 18),
                    new ScopeResponse.SupplySeries("up-agra", "wheat", "RABI", 1997, 2014, 18),
                    new ScopeResponse.SupplySeries("up-agra", "maize", "KHARIF", 1997, 2014, 18),
                    new ScopeResponse.SupplySeries("up-lucknow", "wheat", "RABI", 1998, 2014, 17)),
            List.of(),
            List.of(new ScopeResponse.Dataset("DES_S01_DATA_GOV_IN", "s01-test", "2014")));

    private ReferenceTestSupport() {
    }

    /** ML health as a service with a READY supply model reports it (test fixture). */
    public static final MlHealthResponse HEALTH = new MlHealthResponse("UP", "agri-ml", "0.1.0", "test",
            List.of(new MlHealthResponse.Model("SUPPLY", "READY", "supply-test", "s01-test", "supply-features-v2",
                    null)));

    public static void sync(MlClient mlClient, ReferenceSyncService syncService) {
        when(mlClient.scope()).thenReturn(SCOPE);
        when(mlClient.health()).thenReturn(HEALTH);
        syncService.sync(null);
    }
}
