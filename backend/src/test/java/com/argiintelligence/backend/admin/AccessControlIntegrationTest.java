package com.argiintelligence.backend.admin;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.argiintelligence.backend.auth.AuthTestSupport;
import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.MlServiceException;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import com.argiintelligence.backend.reference.ReferenceTestSupport;
import com.argiintelligence.backend.reference.service.ReferenceSyncService;
import com.argiintelligence.backend.user.entity.Role;
import com.argiintelligence.backend.user.entity.UserDistrictAssignment;
import com.argiintelligence.backend.user.repository.UserDistrictAssignmentRepository;
import com.argiintelligence.backend.user.repository.UserRepository;
import com.argiintelligence.backend.weather.provider.WeatherProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC (MASTER_SPEC §4), regional view (§6.6), admin API (§6.7), legacy farms (D13), audit (§5.1 V6) and
 * correlation ids (D15). Users are registered through the API; roles and assignments are then set directly in
 * the database, as only an ADMIN could otherwise.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccessControlIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    UserDistrictAssignmentRepository assignments;

    @Autowired
    ReferenceSyncService referenceSync;

    @MockitoBean
    MlClient mlClient;

    @MockitoBean
    WeatherProvider weather;

    private Account farmer;
    private Account fpo;
    private Account officer;
    private Account admin;
    private String agraFarm;
    private String lucknowFarm;

    private record Account(String id, String email, String bearer) {
    }

    @BeforeEach
    void setUp() throws Exception {
        ReferenceTestSupport.sync(mlClient, referenceSync);
        when(weather.name()).thenReturn("OPEN_METEO");
        when(weather.fetch(anyDouble(), anyDouble(), anyInt())).thenReturn(forecast());
        when(mlClient.predictSupply(any())).thenReturn(golden());

        farmer = account(Role.FARMER);
        fpo = account(Role.FPO, "up-agra");
        officer = account(Role.AGRICULTURAL_OFFICER, "up-agra");
        admin = account(Role.ADMIN);
        agraFarm = farm(farmer, "up-agra");
        lucknowFarm = farm(farmer, "up-lucknow");
    }

    // ---- §4.2 authorization matrix ----

    @Test
    void authorizationMatrix() throws Exception {
        String supply = "/api/intelligence/supply?districtId=up-agra&cropId=potato&season=RABI&cropYear=2015";
        String weatherPoint = "/api/weather?latitude=26.1&longitude=80.1";
        Object[][] matrix = {
                // method, path, FARMER, FPO, OFFICER, ADMIN
                {HttpMethod.GET, "/api/auth/me", 200, 200, 200, 200},
                {HttpMethod.GET, "/api/reference/scope", 200, 200, 200, 200},
                {HttpMethod.GET, weatherPoint, 200, 200, 200, 200},
                {HttpMethod.GET, supply, 200, 200, 200, 200},
                {HttpMethod.GET, "/api/farms", 200, 200, 403, 403},
                {HttpMethod.GET, "/api/farms/" + agraFarm + "/risk?cropId=wheat", 200, 404, 403, 403},
                {HttpMethod.GET, "/api/regional/districts", 403, 200, 200, 200},
                {HttpMethod.GET, "/api/regional/farms/" + agraFarm, 403, 200, 200, 200},
                {HttpMethod.GET, "/api/admin/users", 403, 403, 403, 200},
                {HttpMethod.GET, "/api/admin/audit", 403, 403, 403, 200},
                {HttpMethod.GET, "/api/admin/farms/unowned", 403, 403, 403, 200},
        };
        Account[] roles = {farmer, fpo, officer, admin};
        for (Object[] row : matrix) {
            for (int i = 0; i < roles.length; i++) {
                int expected = (int) row[2 + i];
                Role role = Role.values()[i]; // FARMER, FPO, AGRICULTURAL_OFFICER, ADMIN: the order of `roles`
                mvc.perform(request((HttpMethod) row[0], (String) row[1]).header(AUTH, roles[i].bearer()))
                        .andExpect(result -> assertThat(result.getResponse().getStatus())
                                .as("%s %s as %s", row[0], row[1], role).isEqualTo(expected));
            }
            mvc.perform(request((HttpMethod) row[0], (String) row[1]))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        // Writes: only owners create farms; nobody writes through the regional view.
        mvc.perform(post("/api/farms").header(AUTH, officer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(farmJson("up-agra"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/farms").header(AUTH, admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(farmJson("up-agra"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/regional/farms/{id}", agraFarm).header(AUTH, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(farmJson("up-agra")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void clientSuppliedOwnerAndRoleAreIgnored() throws Exception {
        String body = farmJson("up-agra").replaceFirst("\\{", "{ \"ownerId\": \"" + admin.id() + "\", ");
        String created = mvc.perform(post("/api/farms").header(AUTH, fpo.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        assertThat(jdbc.queryForObject("select owner_id::text from farm where id = ?::uuid", String.class, id))
                .isEqualTo(fpo.id());
    }

    // ---- §6.6 regional view ----

    @Test
    void officerSeesOnlyAssignedDistrictsAndNoOwnerData() throws Exception {
        mvc.perform(get("/api/regional/districts").header(AUTH, officer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].districtId", contains("up-agra")))
                .andExpect(jsonPath("$[0].label").value("Agra"));
        mvc.perform(get("/api/regional/farms").header(AUTH, officer.bearer()).param("districtId", "up-agra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(agraFarm)))
                .andExpect(jsonPath("$[*].id", not(hasItem(lucknowFarm))))
                .andExpect(jsonPath("$[0].districtLabel").value("Agra"))
                .andExpect(jsonPath("$[0].ownerEmail").doesNotExist())
                .andExpect(jsonPath("$[0].owner").doesNotExist());
        mvc.perform(get("/api/regional/farms").header(AUTH, officer.bearer()).param("districtId", "up-lucknow"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DISTRICT_NOT_FOUND"));
        mvc.perform(get("/api/regional/farms/{id}", lucknowFarm).header(AUTH, officer.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
        mvc.perform(get("/api/regional/farms/{id}", agraFarm).header(AUTH, officer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.districtId").value("up-agra"));
        mvc.perform(get("/api/regional/farms/{id}/weather", agraFarm).header(AUTH, officer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyProvenance.dataClassification").value("FORECAST"));
        mvc.perform(get("/api/regional/farms/{id}/crop-evidence", agraFarm).header(AUTH, officer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankingRule").value("crop-evidence-v1"));
        mvc.perform(get("/api/regional/farms/{id}/risk", agraFarm).header(AUTH, officer.bearer())
                        .param("cropId", "wheat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketRisk.level").value("UNAVAILABLE"));
    }

    @Test
    void adminSeesEveryDistrict() throws Exception {
        mvc.perform(get("/api/regional/districts").header(AUTH, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].districtId", hasItem("up-lucknow")));
        mvc.perform(get("/api/regional/farms/{id}", lucknowFarm).header(AUTH, admin.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void legacyOwnerlessFarmsAreNeverInTheRegionalView() throws Exception {
        String legacy = legacyFarm("up-agra");
        mvc.perform(get("/api/regional/farms/{id}", legacy).header(AUTH, admin.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/regional/farms").header(AUTH, admin.bearer()).param("districtId", "up-agra"))
                .andExpect(jsonPath("$[*].id", not(hasItem(legacy))));
        mvc.perform(get("/api/farms/{id}", legacy).header(AUTH, farmer.bearer()))
                .andExpect(status().isNotFound());
    }

    // ---- §6.7 users, roles, districts ----

    @Test
    void adminListsUsersWithFiltersAndPaging() throws Exception {
        mvc.perform(get("/api/admin/users").header(AUTH, admin.bearer()).param("role", "AGRICULTURAL_OFFICER")
                        .param("q", officer.email().substring(0, 20).toUpperCase()).param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(officer.id())))
                .andExpect(jsonPath("$.items[0].assignedDistrictIds", contains("up-agra")))
                .andExpect(jsonPath("$.items[0].enabled").value(true))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/admin/users").header(AUTH, admin.bearer()).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleAndEnabledChangesApplyImmediatelyAndAreAudited() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}", farmer.id()).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"FPO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FPO"));
        // The same token now carries FPO rights: the principal is reloaded on every request.
        mvc.perform(get("/api/regional/districts").header(AUTH, farmer.bearer())).andExpect(status().isOk());

        mvc.perform(patch("/api/admin/users/{id}", farmer.id()).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/api/auth/me").header(AUTH, farmer.bearer())).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/audit").header(AUTH, admin.bearer()).param("action", "ROLE_CHANGED")
                        .param("actorUserId", admin.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].targetId").value(farmer.id()))
                .andExpect(jsonPath("$.items[0].details.from").value("FARMER"))
                .andExpect(jsonPath("$.items[0].details.to").value("FPO"));
        mvc.perform(get("/api/admin/audit").header(AUTH, admin.bearer()).param("action", "USER_DISABLED")
                        .param("actorUserId", admin.id()))
                .andExpect(jsonPath("$.items[0].targetId").value(farmer.id()));
        mvc.perform(patch("/api/admin/users/{id}", UUID.randomUUID()).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void anAdminCannotDemoteOrDisableThemselves() throws Exception {
        mvc.perform(patch("/api/admin/users/{id}", admin.id()).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"FARMER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(patch("/api/admin/users/{id}", admin.id()).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict());
        assertThat(users.findById(UUID.fromString(admin.id())).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void districtAssignmentRules() throws Exception {
        mvc.perform(put("/api/admin/users/{id}/districts", farmer.id()).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"districtIds\":[\"up-agra\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/users/{id}/districts", officer.id()).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"districtIds\":[\"up-nowhere\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("districtIds"));
        mvc.perform(put("/api/admin/users/{id}/districts", officer.id()).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"districtIds\":[\"up-lucknow\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedDistrictIds", contains("up-lucknow")));
        // The assignment replaces the old set immediately.
        mvc.perform(get("/api/regional/farms/{id}", lucknowFarm).header(AUTH, officer.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/regional/farms/{id}", agraFarm).header(AUTH, officer.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/me").header(AUTH, officer.bearer()))
                .andExpect(jsonPath("$.assignedDistrictIds", contains("up-lucknow")));
        mvc.perform(get("/api/admin/audit").header(AUTH, admin.bearer()).param("action", "DISTRICTS_ASSIGNED")
                        .param("actorUserId", admin.id()))
                .andExpect(jsonPath("$.items[0].details.districtIds[0]").value("up-lucknow"));
    }

    // ---- D13 legacy farms ----

    @Test
    void legacyFarmCanBeGivenAFirstOwnerOnce() throws Exception {
        String legacy = legacyFarm(null);
        mvc.perform(get("/api/admin/farms/unowned").header(AUTH, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(legacy)));

        mvc.perform(post("/api/admin/farms/{id}/owner", legacy).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + officer.id() + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/farms/{id}/owner", legacy).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        mvc.perform(post("/api/admin/farms/{id}/owner", legacy).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + farmer.id() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(legacy));
        // The owner is really persisted: the new owner can now read the farm, and it left the unowned list.
        mvc.perform(get("/api/farms/{id}", legacy).header(AUTH, farmer.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/farms/unowned").header(AUTH, admin.bearer()))
                .andExpect(jsonPath("$[*].id", not(hasItem(legacy))));

        // Never transferred: a farm that has an owner cannot be reassigned.
        mvc.perform(post("/api/admin/farms/{id}/owner", legacy).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + fpo.id() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(post("/api/admin/farms/{id}/owner", agraFarm).header(AUTH, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + fpo.id() + "\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select owner_id::text from farm where id = ?::uuid", String.class, agraFarm))
                .isEqualTo(farmer.id());
        mvc.perform(get("/api/admin/audit").header(AUTH, admin.bearer()).param("action", "FARM_OWNER_ASSIGNED")
                        .param("actorUserId", admin.id()))
                .andExpect(jsonPath("$.items[0].targetId").value(legacy));
    }

    // ---- §6.7 system, ML, reference ----

    @Test
    void systemHealthReportsRealComponentStates() throws Exception {
        doThrow(MlServiceException.unavailable()).when(mlClient).health();
        mvc.perform(get("/api/admin/system/health").header(AUTH, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.mlService.status").value("DOWN"))
                .andExpect(jsonPath("$.mlService.latencyMs").value(nullValue()))
                .andExpect(jsonPath("$.weatherProvider.provider").value("OPEN_METEO"))
                .andExpect(jsonPath("$.referenceSync.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.referenceSync.lastSyncedAt").isNotEmpty());
    }

    @Test
    void mlModelsShowRealMetadataOrTheError() throws Exception {
        mvc.perform(get("/api/admin/ml/models").header(AUTH, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mlError").value(nullValue()))
                .andExpect(jsonPath("$.models[0].capability").value("SUPPLY"))
                .andExpect(jsonPath("$.datasets[0].source").value("DES_S01_DATA_GOV_IN"))
                .andExpect(jsonPath("$.modelEvaluation.testPeriod").value("2013-2014"));

        doThrow(MlServiceException.unavailable()).when(mlClient).health();
        mvc.perform(get("/api/admin/ml/models").header(AUTH, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mlError").value("ML_UNAVAILABLE"))
                .andExpect(jsonPath("$.models").isEmpty())
                .andExpect(jsonPath("$.modelEvaluation").value(nullValue()));
    }

    @Test
    void aFailedReferenceSyncIsRecordedAndKeepsTheExistingScope() throws Exception {
        doThrow(MlServiceException.unavailable()).when(mlClient).scope();
        mvc.perform(post("/api/admin/reference/sync").header(AUTH, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("ML_UNAVAILABLE"));
        mvc.perform(get("/api/reference/scope").header(AUTH, farmer.bearer()))
                .andExpect(jsonPath("$.districts[*].districtId", hasItem("up-agra")))
                .andExpect(jsonPath("$.syncedAt").isNotEmpty());
        mvc.perform(get("/api/admin/audit").header(AUTH, admin.bearer()).param("action", "REFERENCE_SYNCED")
                        .param("actorUserId", admin.id()))
                .andExpect(jsonPath("$.items[0].details.status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].details.reason").value("ML_UNAVAILABLE"));
    }

    // ---- D15 correlation ids and §5.1 auth audit ----

    @Test
    void requestIdsAreEchoedOnSuccessAndErrorsAndRecordedInAudit() throws Exception {
        mvc.perform(get("/api/auth/me").header(AUTH, farmer.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));
        mvc.perform(get("/api/auth/me").header("X-Request-Id", "client-abc_123.4"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "client-abc_123.4"));
        mvc.perform(get("/api/farms/{id}", UUID.randomUUID()).header(AUTH, farmer.bearer())
                        .header("X-Request-Id", "bad id\nwith newline"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));

        String email = AuthTestSupport.uniqueEmail();
        MvcResult registered = mvc.perform(post("/api/auth/register").header("X-Request-Id", "reg-" + email.hashCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AuthTestSupport.registerJson("Audited", email, AuthTestSupport.PASSWORD)))
                .andExpect(status().isCreated()).andReturn();
        String userId = JsonPath.read(registered.getResponse().getContentAsString(), "$.id");
        Map<String, Object> row = jdbc.queryForMap(
                "select request_id, action from audit_event where target_id = ? and action = 'USER_REGISTERED'", userId);
        assertThat(row.get("request_id")).isEqualTo("reg-" + email.hashCode());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        String details = jdbc.queryForObject("select details::text from audit_event where action = 'LOGIN_FAILED' "
                + "and details->>'email' = ? order by occurred_at desc limit 1", String.class, email);
        assertThat(details).contains(email).doesNotContain("wrong-password");
    }

    // ---- fixtures ----

    private Account account(Role role, String... districts) throws Exception {
        String email = AuthTestSupport.uniqueEmail();
        String id = AuthTestSupport.register(mvc, email);
        String bearer = "Bearer " + AuthTestSupport.login(mvc, email);
        var user = users.findById(UUID.fromString(id)).orElseThrow();
        user.setRole(role);
        users.save(user);
        for (String d : districts) {
            assignments.save(new UserDistrictAssignment(user.getId(), d, null));
        }
        return new Account(id, email, bearer);
    }

    private static String farmJson(String districtId) {
        return """
                { "name": "RBAC Farm", "area": 2, "areaUnit": "HECTARE", "irrigationType": "CANAL", "season": "RABI",
                  "districtId": "%s",
                  "location": { "latitude": 26.2, "longitude": 80.2, "state": "Uttar Pradesh", "district": "Agra" },
                  "soilProfile": { "ph": 6.5, "source": "LAB_REPORT", "dataClassification": "OBSERVED" } }
                """.formatted(districtId);
    }

    private String farm(Account owner, String districtId) throws Exception {
        String body = mvc.perform(post("/api/farms").header(AUTH, owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(farmJson(districtId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** A farm as it exists from before V3: no owner. Written directly, as no API can create one. */
    private String legacyFarm(String districtId) {
        UUID location = UUID.randomUUID();
        UUID farm = UUID.randomUUID();
        jdbc.update("insert into farm_location (id, latitude, longitude, state, district) "
                + "values (?, 26.9, 80.9, 'Uttar Pradesh', 'Agra')", location);
        jdbc.update("insert into farm (id, name, area, area_unit, irrigation_type, season, location_id, district_id, "
                + "created_at, updated_at) values (?, 'Legacy', 2, 'HECTARE', 'DRIP', 'RABI', ?, ?, now(), now())",
                farm, location, districtId);
        return farm.toString();
    }

    private static WeatherProvider.Report forecast() {
        return new WeatherProvider.Report("OPEN_METEO", new WeatherProvider.Current(null, null, null, null, null),
                DataClassification.ESTIMATED, List.of(),
                List.of(new WeatherProvider.Day(LocalDate.parse("2026-09-29"), 15.0, 28.0, 1.0, null, null)),
                DataClassification.FORECAST);
    }

    private static SupplyPredictionResponse golden() throws IOException {
        try (InputStream in = AccessControlIntegrationTest.class.getResourceAsStream("/contracts/ml/supply-response.json")) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\"SYNTHETIC\"", "\"OBSERVED\"");
            return JsonMapper.builder().build().readValue(json, SupplyPredictionResponse.class);
        }
    }
}
