package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeaveIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private Session addMember(Session owner, String email) throws Exception {
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", "MEMBER"))))
                .andExpect(status().isCreated());
        String token = email().lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "Emp", "lastName", "Loyee", "password", PW))))
                .andExpect(status().isOk());
        return login(email, PW);
    }

    private String requestLeave(Session s, String type, String start, String end) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/people/leave")
                        .header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", type, "startDate", start, "endDate", end, "reason", "time off"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void member_requests_leave_and_admin_approves_updating_balance() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session emp = addMember(owner, "emp@acme.com");
        String year = String.valueOf(LocalDate.now().getYear());

        // 5-day vacation request (Mon-Fri span → 5 inclusive days)
        String reqId = requestLeave(emp, "VACATION", year + "-08-03", year + "-08-07");

        // pending balance reflects it
        getJson("/api/v1/people/leave/balance", emp);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/people/leave/balance").header("Authorization", "Bearer " + emp.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowanceDays").value(25))
                .andExpect(jsonPath("$.usedDays").value(0))
                .andExpect(jsonPath("$.pendingDays").value(5));

        // appears in the admin inbox
        JsonNode inbox = getJson("/api/v1/people/leave", owner);
        assertThat(inbox.size()).isEqualTo(1);

        // admin approves
        mockMvc.perform(post("/api/v1/people/leave/" + reqId + "/approve")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // balance now shows 5 used, 20 remaining
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/people/leave/balance").header("Authorization", "Bearer " + emp.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedDays").value(5))
                .andExpect(jsonPath("$.remainingDays").value(20));
    }

    @Test
    void member_cannot_approve_and_cannot_see_the_inbox() throws Exception {
        Session owner = onboardOwner("Acme", "owner2@acme.com", PW);
        Session emp = addMember(owner, "emp2@acme.com");
        String year = String.valueOf(LocalDate.now().getYear());
        String reqId = requestLeave(emp, "SICK", year + "-09-01", year + "-09-01");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/people/leave").header("Authorization", "Bearer " + emp.accessToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/people/leave/" + reqId + "/approve")
                        .header("Authorization", "Bearer " + emp.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requester_can_cancel_their_pending_request() throws Exception {
        Session owner = onboardOwner("Acme", "owner3@acme.com", PW);
        Session emp = addMember(owner, "emp3@acme.com");
        String year = String.valueOf(LocalDate.now().getYear());
        String reqId = requestLeave(emp, "PERSONAL", year + "-10-10", year + "-10-11");

        mockMvc.perform(post("/api/v1/people/leave/" + reqId + "/cancel")
                        .header("Authorization", "Bearer " + emp.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void invalid_date_range_is_rejected() throws Exception {
        Session owner = onboardOwner("Acme", "owner4@acme.com", PW);
        String year = String.valueOf(LocalDate.now().getYear());
        mockMvc.perform(post("/api/v1/people/leave")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "VACATION", "startDate", year + "-05-10", "endDate", year + "-05-01"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leave_requests_are_isolated_across_tenants() throws Exception {
        Session ownerA = onboardOwner("Company A", "a@a.com", PW);
        Session ownerB = onboardOwner("Company B", "b@b.com", PW);
        String year = String.valueOf(LocalDate.now().getYear());
        String reqA = requestLeave(ownerA, "VACATION", year + "-07-01", year + "-07-03");

        // B's inbox is empty and B cannot approve A's request
        JsonNode bInbox = getJson("/api/v1/people/leave", ownerB);
        assertThat(bInbox.size()).isZero();
        mockMvc.perform(post("/api/v1/people/leave/" + reqA + "/approve")
                        .header("Authorization", "Bearer " + ownerB.accessToken()))
                .andExpect(status().isNotFound());
    }
}
