package dev.skergan.calcurve.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class CalibrationControllerTest {

    static final String STANDARDS = """
            "standards": [
              {"concentration": 0,  "response": 2.1},
              {"concentration": 2,  "response": 5.0},
              {"concentration": 4,  "response": 9.0},
              {"concentration": 6,  "response": 12.6},
              {"concentration": 8,  "response": 17.3},
              {"concentration": 10, "response": 21.0},
              {"concentration": 12, "response": 24.7}
            ]""";

    static final String CALIBRATION = """
            {
              "analyte": "Fluorescein",
              "concentrationUnit": "pg/mL",
              "responseUnit": "fluorescence",
              %s,
              "minRSquared": 0.995
            }""".formatted(STANDARDS);

    static final String QUANTIFY = """
            {
              "analyte": "Fluorescein",
              "concentrationUnit": "pg/mL",
              %s,
              "unknowns": [
                {"sampleId": "S-01", "responses": [2.9]},
                {"sampleId": "S-02", "responses": [13.5]},
                {"sampleId": "S-03", "responses": [23.0]},
                {"sampleId": "S-04", "responses": [13.5, 13.3, 13.7]}
              ]
            }""".formatted(STANDARDS);

    @Autowired
    MockMvc mvc;

    @Test
    void calibratesExample() throws Exception {
        postJson("/api/v1/calibrations", CALIBRATION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyte").value("Fluorescein"))
                .andExpect(jsonPath("$.n").value(7))
                .andExpect(jsonPath("$.slope", closeTo(1.93036, 1e-4)))
                .andExpect(jsonPath("$.intercept", closeTo(1.51786, 1e-4)))
                .andExpect(jsonPath("$.rSquared", closeTo(0.997760, 1e-4)))
                .andExpect(jsonPath("$.lod", closeTo(0.739965, 1e-5)))
                .andExpect(jsonPath("$.calibratedRange.max").value(12))
                .andExpect(jsonPath("$.residuals", hasSize(7)))
                .andExpect(jsonPath("$.flags", hasSize(0)));
    }

    @Test
    void roundsOutputToSixSignificantFigures() throws Exception {
        String body = postJson("/api/v1/calibrations", CALIBRATION).andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("\"slope\":1.93036,")
                .contains("\"loq\":2.24232,");
    }

    @Test
    void twoStandardsAre422() throws Exception {
        postJson("/api/v1/calibrations", """
                {"standards": [{"concentration": 0, "response": 1}, {"concentration": 2, "response": 5}]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://calcurve.dev/problems/too-few-standards"))
                .andExpect(jsonPath("$.flags[0].code").value("TOO_FEW_STANDARDS"))
                .andExpect(jsonPath("$.flags[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.flags[0].error").doesNotExist());
    }

    @Test
    void identicalConcentrationsAre422() throws Exception {
        postJson("/api/v1/calibrations", """
                {"standards": [{"concentration": 1, "response": 1},
                               {"concentration": 1, "response": 2},
                               {"concentration": 1, "response": 3}]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://calcurve.dev/problems/degenerate-standards"));
    }

    @Test
    void missingStandardsIs400() throws Exception {
        postJson("/api/v1/calibrations", "{\"analyte\": \"Fluorescein\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", hasItem("standards")));
    }

    @Test
    void negativeConcentrationIs400() throws Exception {
        postJson("/api/v1/calibrations", """
                {"standards": [{"concentration": -1, "response": 1}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("standards[0].concentration")));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        postJson("/api/v1/calibrations", "{\"standards\": [")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://calcurve.dev/problems/malformed-request"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void quantifiesUnknowns() throws Exception {
        postJson("/api/v1/calibrations/quantify", QUANTIFY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calibration.slope", closeTo(1.93036, 1e-4)))
                .andExpect(jsonPath("$.results", hasSize(4)))
                .andExpect(jsonPath("$.results[0].concentration", closeTo(0.716004, 1e-4)))
                .andExpect(jsonPath("$.results[0].flags[0].code").value("BELOW_LOD"))
                .andExpect(jsonPath("$.results[0].flags[0].message")
                        .value("0.716 pg/mL is below the LOD of 0.740 pg/mL"))
                .andExpect(jsonPath("$.results[3].replicates").value(3))
                .andExpect(jsonPath("$.results[3].ci95", closeTo(0.397918, 1e-4)));
    }

    @Test
    void blankSampleIdIs400() throws Exception {
        postJson("/api/v1/calibrations/quantify", """
                {%s, "unknowns": [{"sampleId": " ", "responses": []}]}""".formatted(STANDARDS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("unknowns[0].sampleId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("unknowns[0].responses")));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
