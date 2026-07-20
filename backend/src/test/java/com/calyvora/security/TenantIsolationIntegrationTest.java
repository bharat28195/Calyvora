package com.calyvora.security;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Sprint-1 merge gate (SD-2): a user of company A can never see or affect company B's data.
 * These adversarial cross-tenant checks must pass before any feature ships.
 */
class TenantIsolationIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "password1234";

    @Test
    void protected_endpoints_reject_unauthenticated_requests() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/company/members")).andExpect(status().isUnauthorized());
    }

    @Test
    void company_A_cannot_revoke_company_B_invitation() throws Exception {
        Session ownerA = onboardOwner("Company A", "a@a.com", PASSWORD);
        Session ownerB = onboardOwner("Company B", "b@b.com", PASSWORD);

        // A creates an invitation
        MvcResult created = mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "x@a.com", "role", "MEMBER"))))
                .andExpect(status().isCreated())
                .andReturn();
        String invitationId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        // B tries to revoke A's invitation → must be 404 (invisible across tenants)
        mockMvc.perform(delete("/api/v1/invitations/" + invitationId)
                        .header("Authorization", "Bearer " + ownerB.accessToken()))
                .andExpect(status().isNotFound());

        // A's invitation is still pending
        JsonNode aPending = getJson("/api/v1/invitations", ownerA);
        assertThat(aPending.size()).isEqualTo(1);
    }

    @Test
    void members_and_dashboard_are_scoped_to_the_callers_company() throws Exception {
        Session ownerA = onboardOwner("Company A", "owner-a@a.com", PASSWORD);
        Session ownerB = onboardOwner("Company B", "owner-b@b.com", PASSWORD);

        // A adds a member
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "member-a@a.com", "role", "MEMBER"))))
                .andExpect(status().isCreated());
        String token = email.lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "M", "lastName", "A",
                                "password", PASSWORD))))
                .andExpect(status().isOk());

        // B sees only itself (1 member); A sees 2
        JsonNode bMembers = getJson("/api/v1/company/members", ownerB);
        assertThat(bMembers.size()).isEqualTo(1);
        assertThat(bMembers.get(0).get("email").asText()).isEqualTo("owner-b@b.com");

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + ownerB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.companyName").value("Company B"));

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + ownerA.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));
    }
}
