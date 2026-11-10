package com.calyvora.auth;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Each person's language, clock and date style (V68). English unless someone chooses otherwise. */
class UserPreferencesTest extends IntegrationTestBase {

    private MvcResult save(Session s, String language, String timezone, String dateFormat, String timeFormat,
                           int expected) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("language", language);
        body.put("timezone", timezone);
        body.put("dateFormat", dateFormat);
        body.put("timeFormat", timeFormat);
        return mockMvc.perform(put("/api/v1/auth/preferences")
                        .header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is(expected))
                .andReturn();
    }

    @Test
    void english_and_the_company_clock_until_someone_chooses() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");
        JsonNode me = getJson("/api/v1/auth/me", owner);
        assertThat(me.get("language").asText()).isEqualTo("en");
        assertThat(me.get("timezone").asText()).isEqualTo(me.get("company").get("timezone").asText());
        JsonNode prefs = me.get("user").get("preferences");
        assertThat(prefs.get("language").isNull()).isTrue();
        assertThat(prefs.get("dateFormat").isNull()).isTrue();
    }

    @Test
    void a_choice_is_saved_and_comes_back_on_me() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");
        save(owner, "hi", "America/New_York", "MDY", "H12", 200);

        JsonNode me = getJson("/api/v1/auth/me", owner);
        assertThat(me.get("language").asText()).isEqualTo("hi");
        assertThat(me.get("timezone").asText()).isEqualTo("America/New_York");
        JsonNode prefs = me.get("user").get("preferences");
        assertThat(prefs.get("timezone").asText()).isEqualTo("America/New_York");
        assertThat(prefs.get("dateFormat").asText()).isEqualTo("MDY");
        assertThat(prefs.get("timeFormat").asText()).isEqualTo("H12");

        // Blank puts everything back to the defaults.
        save(owner, "", "", "", "", 200);
        me = getJson("/api/v1/auth/me", owner);
        assertThat(me.get("language").asText()).isEqualTo("en");
        assertThat(me.get("timezone").asText()).isEqualTo(me.get("company").get("timezone").asText());
    }

    @Test
    void the_company_language_is_the_default_for_its_people() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");
        mockMvc.perform(patch("/api/v1/company/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("timezone", "Asia/Kolkata", "locale", "fr", "currency", "INR"))))
                .andExpect(status().isOk());
        assertThat(getJson("/api/v1/auth/me", owner).get("language").asText()).isEqualTo("fr");

        save(owner, "es", null, null, null, 200);
        assertThat(getJson("/api/v1/auth/me", owner).get("language").asText())
                .as("a person's own choice wins over the company's").isEqualTo("es");
    }

    @Test
    void unknown_values_are_refused() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");
        save(owner, "klingon", null, null, null, 400);
        save(owner, "en", "IST", null, null, 400);
        save(owner, "en", null, "DDMMYY", null, 400);
        save(owner, "en", null, null, "H25", 400);
    }

    @Test
    void the_platform_owner_without_an_employee_record_keeps_a_clock_too() throws Exception {
        platformOwner.ensurePlatformOwner();
        Session platform = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        save(platform, "en", "America/Los_Angeles", "MDY", "H12", 200);
        assertThat(getJson("/api/v1/auth/me", platform).get("timezone").asText()).isEqualTo("America/Los_Angeles");
    }

    @Test
    void preferences_need_you_to_be_signed_in() throws Exception {
        mockMvc.perform(put("/api/v1/auth/preferences").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
