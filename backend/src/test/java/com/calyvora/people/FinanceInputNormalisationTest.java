package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saving bank details kept failing for input that is correct in every way a person would recognise:
 * an IFSC typed in lower case, a UAN written in spaced groups. The identifiers are normalised before
 * validation now, and a genuinely malformed value comes back naming the field and what it expects —
 * the old response was a bare "Validation failed" with no field attached.
 */
class FinanceInputNormalisationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Test
    void an_ifsc_typed_in_lower_case_is_accepted_and_stored_upper_case() throws Exception {
        seed();
        Session sara = login("sara.okoro@northwind.demo", PW);

        mockMvc.perform(patch("/api/v1/people/me/finance")
                        .header("Authorization", "Bearer " + sara.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bankIfsc", "hdfc0003939"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankIfsc").value("HDFC0003939"));
    }

    @Test
    void surrounding_and_internal_whitespace_is_ignored() throws Exception {
        seed();
        Session sara = login("sara.okoro@northwind.demo", PW);

        mockMvc.perform(patch("/api/v1/people/me/finance")
                        .header("Authorization", "Bearer " + sara.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bankIfsc", "  hdfc0003939 "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankIfsc").value("HDFC0003939"));
    }

    @Test
    void a_uan_written_in_spaced_groups_is_accepted() throws Exception {
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);
        String employeeId = getJson("/api/v1/people/me", ava).get("id").asText();

        // UAN is an employer-owned field, so it goes through the HR endpoint rather than /me.
        mockMvc.perform(patch("/api/v1/people/employees/" + employeeId + "/finance")
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("uan", "1001 2345 6789"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uan").value("100123456789"));
    }

    @Test
    void a_genuinely_malformed_value_names_the_field_and_what_it_expects() throws Exception {
        seed();
        Session sara = login("sara.okoro@northwind.demo", PW);

        mockMvc.perform(patch("/api/v1/people/me/finance")
                        .header("Authorization", "Bearer " + sara.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bankIfsc", "NOTANIFSC"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bankIfsc"))
                .andExpect(jsonPath("$.errors[0].message").value(
                        org.hamcrest.Matchers.containsString("11 characters")));
    }

    @Test
    void every_bad_field_is_reported_at_once_rather_than_one_at_a_time() throws Exception {
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);
        String employeeId = getJson("/api/v1/people/me", ava).get("id").asText();

        mockMvc.perform(patch("/api/v1/people/employees/" + employeeId + "/finance")
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bankIfsc", "BAD", "panNumber", "BAD", "uan", "12"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    private void seed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }
}
