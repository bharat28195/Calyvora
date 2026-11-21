package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PD-81: location at check-in — off, recorded, enforced. */
class CheckinLocationIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";
    private static final String BASE = "/api/v1/people/attendance/location";

    // An office in Bengaluru, and two points: ~50 m away and ~5 km away.
    private static final double LAT = 12.9716, LNG = 77.5946;
    private static final Map<String, Object> NEAR = Map.of("latitude", 12.97205, "longitude", 77.5946, "accuracy", 20);
    private static final Map<String, Object> FAR = Map.of("latitude", 13.0166, "longitude", 77.5946, "accuracy", 20);

    @Test
    void off_by_default_and_nothing_is_stored() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        assertThat(getJson(BASE, owner).get("mode").asText()).isEqualTo("OFF");

        checkIn(owner, FAR).andExpect(status().isOk());
        assertThat(getJson(BASE + "/day", owner)).isEmpty();
    }

    @Test
    void record_mode_stores_the_office_and_never_refuses() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        setMode(owner, "RECORD");
        addOffice(owner);

        checkIn(owner, NEAR).andExpect(status().isOk());
        checkOut(owner, FAR).andExpect(status().isOk());

        JsonNode spot = getJson(BASE + "/day", owner).get(0);
        assertThat(spot.get("inOffice").asText()).isEqualTo("Head office");
        assertThat(spot.get("inDistanceM").asInt()).isLessThan(100);
        assertThat(spot.get("outOffice").isNull()).isTrue();            // checked out away from the office
        assertThat(spot.get("outDistanceM").asInt()).isGreaterThan(4000);
    }

    @Test
    void enforce_refuses_outside_the_office_and_without_location() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        setMode(owner, "ENFORCE");
        addOffice(owner);

        checkIn(owner, FAR).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/people/attendance/me/check-in").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());                    // no location at all
        // A refused check-in leaves no trace: today is still open.
        assertThat(getJson("/api/v1/people/attendance/me/today", owner).get("sessions")).isEmpty();

        checkIn(owner, NEAR).andExpect(status().isOk());
    }

    @Test
    void enforce_with_no_offices_set_up_does_not_lock_anyone_out() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        setMode(owner, "ENFORCE");
        checkIn(owner, FAR).andExpect(status().isOk());
    }

    @Test
    void one_company_never_sees_anothers_offices() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session other = onboardOwner("Other", "other@other.com", PW);
        setMode(owner, "RECORD");
        addOffice(owner);
        JsonNode theirs = getJson(BASE, other);
        assertThat(theirs.get("mode").asText()).isEqualTo("OFF");
        assertThat(theirs.get("offices")).isEmpty();
    }

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private void setMode(Session s, String mode) throws Exception {
        mockMvc.perform(put(BASE + "/mode").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("mode", mode))))
                .andExpect(status().isOk());
    }

    private void addOffice(Session s) throws Exception {
        mockMvc.perform(post(BASE + "/offices").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Head office", "latitude", LAT, "longitude", LNG, "radiusM", 200))))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions checkIn(Session s, Map<String, Object> at) throws Exception {
        return mockMvc.perform(post("/api/v1/people/attendance/me/check-in").header("Authorization", bearer(s))
                .contentType(MediaType.APPLICATION_JSON).content(json(at)));
    }

    private org.springframework.test.web.servlet.ResultActions checkOut(Session s, Map<String, Object> at) throws Exception {
        return mockMvc.perform(post("/api/v1/people/attendance/me/check-out").header("Authorization", bearer(s))
                .contentType(MediaType.APPLICATION_JSON).content(json(at)));
    }
}
