package com.argiintelligence.backend.farm;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FarmControllerIntegrationTest {

    // Sample data from the API contract.
    private static final String SAMPLE_FARM = """
            {
              "name": "Green Valley Farm",
              "area": 12.5,
              "areaUnit": "ACRE",
              "irrigationType": "BOREWELL",
              "currentCrop": "Tomato",
              "previousCrop": "Millet",
              "season": "KHARIF",
              "location": {
                "latitude": 12.72,
                "longitude": 77.28,
                "state": "Karnataka",
                "district": "Ramanagara",
                "taluk": "Ramanagara",
                "addressLabel": "Ramanagara, Karnataka"
              },
              "soilProfile": {
                "ph": 6.4,
                "electricalConductivity": 0.42,
                "organicCarbon": 0.51,
                "nitrogen": 240,
                "phosphorus": 18,
                "potassium": 190,
                "sulphur": 14,
                "zinc": 0.72,
                "iron": 4.1,
                "manganese": 3.2,
                "copper": 0.41,
                "boron": 0.52,
                "source": "SOIL_HEALTH_CARD",
                "dataClassification": "OBSERVED",
                "measuredAt": "2026-08-14",
                "confidence": 1.0
              }
            }
            """;

    @Autowired
    MockMvc mvc;

    @Test
    void createReturns201WithFullFarm() throws Exception {
        mvc.perform(post("/api/farms").contentType(MediaType.APPLICATION_JSON).content(SAMPLE_FARM))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/farms/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Green Valley Farm"))
                .andExpect(jsonPath("$.area").value(12.5))
                .andExpect(jsonPath("$.areaUnit").value("ACRE"))
                .andExpect(jsonPath("$.irrigationType").value("BOREWELL"))
                .andExpect(jsonPath("$.season").value("KHARIF"))
                .andExpect(jsonPath("$.location.id").isNotEmpty())
                .andExpect(jsonPath("$.location.latitude").value(12.72))
                .andExpect(jsonPath("$.location.longitude").value(77.28))
                .andExpect(jsonPath("$.location.district").value("Ramanagara"))
                .andExpect(jsonPath("$.soilProfile.id").isNotEmpty())
                .andExpect(jsonPath("$.soilProfile.ph").value(6.4))
                .andExpect(jsonPath("$.soilProfile.nitrogen").value(240))
                .andExpect(jsonPath("$.soilProfile.source").value("SOIL_HEALTH_CARD"))
                .andExpect(jsonPath("$.soilProfile.dataClassification").value("OBSERVED"))
                .andExpect(jsonPath("$.soilProfile.measuredAt").value("2026-08-14"))
                .andExpect(jsonPath("$.soilProfile.confidence").value(1.0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void missingSoilMeasurementsStayNull() throws Exception {
        String body = SAMPLE_FARM.replaceAll("\"soilProfile\": \\{[^}]*}", """
                "soilProfile": { "ph": 7.1, "source": "MANUAL", "dataClassification": "ESTIMATED" }""");
        mvc.perform(post("/api/farms").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.soilProfile.ph").value(7.1))
                .andExpect(jsonPath("$.soilProfile.nitrogen").value(nullValue()))
                .andExpect(jsonPath("$.soilProfile.boron").value(nullValue()))
                .andExpect(jsonPath("$.soilProfile.confidence").value(nullValue()))
                .andExpect(jsonPath("$.soilProfile.measuredAt").value(nullValue()))
                .andExpect(jsonPath("$.soilProfile.dataClassification").value("ESTIMATED"));
    }

    @Test
    void getReturnsCreatedFarm() throws Exception {
        String id = createSample();
        mvc.perform(get("/api/farms/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.location.state").value("Karnataka"))
                .andExpect(jsonPath("$.soilProfile.potassium").value(190));
    }

    @Test
    void listIncludesCreatedFarm() throws Exception {
        String id = createSample();
        mvc.perform(get("/api/farms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id)));
    }

    @Test
    void updateReplacesFieldsAndKeepsIds() throws Exception {
        String created = mvc.perform(post("/api/farms").contentType(MediaType.APPLICATION_JSON).content(SAMPLE_FARM))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        String locationId = JsonPath.read(created, "$.location.id");
        String soilId = JsonPath.read(created, "$.soilProfile.id");

        String updated = SAMPLE_FARM
                .replace("Green Valley Farm", "Hill Top Farm")
                .replace("\"district\": \"Ramanagara\"", "\"district\": \"Mandya\"")
                .replace("\"ph\": 6.4", "\"ph\": 7.2")
                .replace("\"currentCrop\": \"Tomato\"", "\"currentCrop\": null");

        mvc.perform(put("/api/farms/{id}", id).contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hill Top Farm"))
                .andExpect(jsonPath("$.currentCrop").value(nullValue()))
                .andExpect(jsonPath("$.location.id").value(locationId))
                .andExpect(jsonPath("$.soilProfile.id").value(soilId));

        mvc.perform(get("/api/farms/{id}", id))
                .andExpect(jsonPath("$.name").value("Hill Top Farm"))
                .andExpect(jsonPath("$.location.district").value("Mandya"))
                .andExpect(jsonPath("$.soilProfile.ph").value(7.2))
                .andExpect(jsonPath("$.createdAt").value(JsonPath.<String>read(created, "$.createdAt")))
                .andExpect(jsonPath("$.updatedAt", not(JsonPath.<String>read(created, "$.updatedAt"))));
    }

    @Test
    void unknownFarmIsNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get("/api/farms/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/farms/" + missing));
        mvc.perform(put("/api/farms/{id}", missing).contentType(MediaType.APPLICATION_JSON).content(SAMPLE_FARM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
    }

    @Test
    void malformedIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/farms/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void invalidFarmIsRejectedWithFieldDetails() throws Exception {
        String invalid = """
                {
                  "name": " ",
                  "area": 0,
                  "areaUnit": "ACRE",
                  "irrigationType": "DRIP",
                  "location": { "latitude": 91, "longitude": 77.28, "state": "Karnataka" },
                  "soilProfile": { "ph": 15, "confidence": 1.5, "dataClassification": "OBSERVED" }
                }
                """;
        mvc.perform(post("/api/farms").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field", containsInAnyOrder(
                        "name", "area", "season",
                        "location.latitude", "location.district",
                        "soilProfile.ph", "soilProfile.confidence", "soilProfile.source")));
    }

    @Test
    void unknownEnumValueIsBadRequest() throws Exception {
        mvc.perform(post("/api/farms").contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_FARM.replace("\"ACRE\"", "\"BIGHA\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void farmWithoutSoilReportsSoilUnavailable() throws Exception {
        String body = SAMPLE_FARM.replaceAll(",\\s*\"soilProfile\": \\{[^}]*}", "");
        String id = JsonPath.read(mvc.perform(post("/api/farms").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.soilProfile").value(nullValue()))
                .andExpect(jsonPath("$.soilDataAvailable").value(false))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/farms/{id}", id))
                .andExpect(jsonPath("$.soilProfile").value(nullValue()))
                .andExpect(jsonPath("$.soilDataAvailable").value(false));

        // Soil can be added later, then removed again by a full-replacement PUT.
        mvc.perform(put("/api/farms/{id}", id).contentType(MediaType.APPLICATION_JSON).content(SAMPLE_FARM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soilProfile.id").isNotEmpty())
                .andExpect(jsonPath("$.soilDataAvailable").value(true));
        mvc.perform(put("/api/farms/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soilProfile").value(nullValue()))
                .andExpect(jsonPath("$.soilDataAvailable").value(false));
    }

    @Test
    void contradictorySoilProvenanceIsRejected() throws Exception {
        String regionalObserved = SAMPLE_FARM.replace("\"SOIL_HEALTH_CARD\"", "\"REGIONAL_ESTIMATE\"");
        String labSynthetic = SAMPLE_FARM.replace("\"SOIL_HEALTH_CARD\"", "\"LAB_REPORT\"")
                .replace("\"OBSERVED\"", "\"SYNTHETIC\"");
        for (String body : new String[]{regionalObserved, labSynthetic}) {
            mvc.perform(post("/api/farms").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INCONSISTENT_SOIL_PROVENANCE"));
        }
        String id = createSample();
        mvc.perform(put("/api/farms/{id}", id).contentType(MediaType.APPLICATION_JSON).content(regionalObserved))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCONSISTENT_SOIL_PROVENANCE"));
        // A consistent combination is still accepted.
        mvc.perform(post("/api/farms").contentType(MediaType.APPLICATION_JSON)
                        .content(regionalObserved.replace("\"OBSERVED\"", "\"ESTIMATED\"")))
                .andExpect(status().isCreated());
    }

    @Test
    void corsAllowsLocalFrontend() throws Exception {
        mvc.perform(options("/api/farms")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    private String createSample() throws Exception {
        String body = mvc.perform(post("/api/farms").contentType(MediaType.APPLICATION_JSON).content(SAMPLE_FARM))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
