package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An exit takes an admin's approval (PD-65): HR asking changes nothing about the person until an
 * admin says yes; an admin starting one is the approval.
 */
class ExitApprovalTest extends IntegrationTestBase {

    private static final String PW = "demopass123";
    private Session admin;
    private Session hr;
    private String priya;

    @BeforeEach
    void seed() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        admin = login("ava.chen@northwind.demo", PW);
        hr = login("leo.martins@northwind.demo", PW);
        priya = null;
        for (JsonNode e : getJson("/api/v1/people/employees", admin)) {
            if (e.toString().contains("Priya")) priya = e.get("id").asText();
        }
        assertThat(priya).isNotNull();
    }

    private org.springframework.test.web.servlet.ResultActions start(Session who) throws Exception {
        return mockMvc.perform(post("/api/v1/people/employees/" + priya + "/exit")
                .header("Authorization", "Bearer " + who.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("lastWorkingDay", LocalDate.now().plusMonths(1).toString(), "reason", "Moving cities"))));
    }

    private org.springframework.test.web.servlet.ResultActions act(Session who, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/people/employees/" + priya + "/exit/" + action)
                .header("Authorization", "Bearer " + who.accessToken()));
    }

    @Test
    @DisplayName("HR asks; nothing changes until an admin approves, then the person is on notice")
    void hr_requests_admin_approves() throws Exception {
        start(hr).andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.requestedReason").value("Moving cities"))
                .andExpect(jsonPath("$.requestedByName").isNotEmpty());

        // Asking twice is refused; HR cannot approve its own request.
        start(hr).andExpect(status().isBadRequest());
        act(hr, "approve").andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/people/exits/requests")
                        .header("Authorization", "Bearer " + hr.accessToken()))
                .andExpect(status().isForbidden());

        JsonNode waiting = getJson("/api/v1/people/exits/requests", admin);
        assertThat(waiting).hasSize(1);
        assertThat(waiting.get(0).get("employeeId").asText()).isEqualTo(priya);
        // The admin hears about it.
        assertThat(getJson("/api/v1/notifications", admin).toString()).contains("EXIT_REQUESTED");

        act(admin, "approve").andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("NOTICE"))
                .andExpect(jsonPath("$.reason").value("Moving cities"))
                .andExpect(jsonPath("$.requestedAt").value(org.hamcrest.Matchers.nullValue()));
        assertThat(getJson("/api/v1/people/exits/requests", admin)).isEmpty();
        assertThat(getJson("/api/v1/notifications", hr).toString()).contains("EXIT_DECIDED");
    }

    @Test
    @DisplayName("an admin can turn a request down, and HR can withdraw one — the person is untouched")
    void reject_and_withdraw() throws Exception {
        start(hr).andExpect(status().isOk());
        act(admin, "reject").andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.requestedAt").value(org.hamcrest.Matchers.nullValue()));

        start(hr).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/people/employees/" + priya + "/exit")
                        .header("Authorization", "Bearer " + hr.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("ACTIVE"));
        assertThat(getJson("/api/v1/people/exits/requests", admin)).isEmpty();
    }

    @Test
    @DisplayName("an admin starting an exit is the approval: on notice at once")
    void admin_starts_directly() throws Exception {
        start(admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("NOTICE"));
    }
}
