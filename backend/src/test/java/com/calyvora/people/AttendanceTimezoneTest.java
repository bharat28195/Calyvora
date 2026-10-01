package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which clock a punch is stamped on.
 *
 * <p>The server runs on UTC and always will. The question is whose day it is: a person in Kolkata
 * pressing "check in" at 09:00 must see 09:00 on the row, on today's date in Kolkata, whatever the
 * host's clock says. Before this, a company that had never opened its settings page got UTC — so the
 * attendance screen said 03:30 for a nine o'clock arrival, and a punch after 05:30 IST fell on the
 * previous day.
 *
 * <p>The tests are written against a large offset (Pacific/Kiritimati, UTC+14) rather than Kolkata's
 * +5:30, so that the assertion is unambiguous at any hour the suite happens to run: a fourteen-hour
 * gap cannot be mistaken for rounding.
 */
class AttendanceTimezoneTest extends IntegrationTestBase {

    private static final String PW = "Passw0rd!x";

    /** A stamp within a couple of minutes of "now" in the given zone, allowing for the request itself. */
    private static void assertStampedIn(String hhmm, ZoneId zone) {
        LocalTime stamped = LocalTime.parse(hhmm);
        LocalTime now = LocalTime.now(zone).withSecond(0).withNano(0);
        long minutesApart = Math.abs(ChronoUnit.MINUTES.between(stamped, now));
        // Wrap-around at midnight counts as close, too.
        minutesApart = Math.min(minutesApart, 1440 - minutesApart);
        assertThat(minutesApart)
                .as("a punch at %s should read as now in %s (%s)", hhmm, zone, now)
                .isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("a company that never opened its settings is on the product default, not UTC")
    void the_default_is_not_utc() throws Exception {
        Session owner = onboardOwner("Zoneco", "admin@zoneco.test", PW);
        JsonNode me = getJson("/api/v1/auth/me", owner);
        // "UTC" was the value for "nobody has chosen yet". It is not any company's timezone.
        assertThat(me.get("timezone").asText()).isEqualTo("Asia/Kolkata");
        assertThat(me.get("company").get("timezone").asText()).isEqualTo("Asia/Kolkata");
    }

    @Test
    @DisplayName("a check-in is stamped where the person is, not where the server is")
    void the_punch_is_in_the_persons_zone() throws Exception {
        Session owner = onboardOwner("Zoneco2", "admin@zoneco2.test", PW);
        ZoneId farAhead = ZoneId.of("Pacific/Kiritimati");

        mockMvc.perform(patch("/api/v1/people/me")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("timezone", farAhead.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value(farAhead.getId()));

        // /me now reports the person's zone as the effective one, so the screen's clock and the
        // server's stamp are resolved by the same rule.
        assertThat(getJson("/api/v1/auth/me", owner).get("timezone").asText()).isEqualTo(farAhead.getId());

        JsonNode row = postJson("/api/v1/people/attendance/me/check-in", null, owner);
        assertStampedIn(row.get("checkIn").asText(), farAhead);
        assertThat(row.get("date").asText())
                .as("the punch lands on today's date in their zone")
                .isEqualTo(LocalDate.now(farAhead).toString());
    }

    @Test
    @DisplayName("clearing the setting goes back to the company's zone")
    void blank_means_same_as_company() throws Exception {
        Session owner = onboardOwner("Zoneco3", "admin@zoneco3.test", PW);
        mockMvc.perform(patch("/api/v1/people/me")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("timezone", "Europe/Berlin"))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/people/me")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("timezone", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").isEmpty());
        assertThat(getJson("/api/v1/auth/me", owner).get("timezone").asText()).isEqualTo("Asia/Kolkata");
    }

    @Test
    @DisplayName("a zone the JDK does not know is refused, not silently ignored")
    void an_unknown_zone_is_an_error() throws Exception {
        Session owner = onboardOwner("Zoneco4", "admin@zoneco4.test", PW);
        // "IST" saved and quietly treated as Kolkata gives the person no way to learn that their
        // setting did nothing — and IST is also Irish Standard Time.
        mockMvc.perform(patch("/api/v1/people/me")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("timezone", "IST"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Asia/Kolkata")));
    }

    private JsonNode postJson(String path, Object body, Session session) throws Exception {
        var req = post(path).header("Authorization", "Bearer " + session.accessToken());
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        String out = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(out);
    }
}
