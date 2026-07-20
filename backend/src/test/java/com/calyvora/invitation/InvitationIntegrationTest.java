package com.calyvora.invitation;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvitationIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "password1234";

    @Test
    void owner_can_invite_and_invitee_can_accept_and_login() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PASSWORD);

        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "grace@acme.com", "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("grace@acme.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.invitedByEmail").value("owner@acme.com"));

        JsonNode pending = getJson("/api/v1/invitations", owner);
        assertThat(pending.size()).isEqualTo(1);

        String token = email.lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "Grace", "lastName", "Hopper",
                                "password", PASSWORD))))
                .andExpect(status().isOk());

        // New member can now log in and is ACTIVE with the invited role.
        Session grace = login("grace@acme.com", PASSWORD);
        assertThat(grace.accessToken()).isNotBlank();

        JsonNode members = getJson("/api/v1/company/members", owner);
        assertThat(members.size()).isEqualTo(2);
    }

    @Test
    void member_cannot_invite() throws Exception {
        Session owner = onboardOwner("Acme", "owner2@acme.com", PASSWORD);
        // invite + accept a MEMBER
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "member@acme.com", "role", "MEMBER"))))
                .andExpect(status().isCreated());
        String token = email.lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "Mem", "lastName", "Ber",
                                "password", PASSWORD))))
                .andExpect(status().isOk());

        Session member = login("member@acme.com", PASSWORD);
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "another@acme.com", "role", "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void inviting_an_already_pending_email_conflicts() throws Exception {
        Session owner = onboardOwner("Acme", "owner3@acme.com", PASSWORD);
        var invite = post("/api/v1/invitations")
                .header("Authorization", "Bearer " + owner.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "dupe@acme.com", "role", "MEMBER")));
        mockMvc.perform(invite).andExpect(status().isCreated());
        mockMvc.perform(invite).andExpect(status().isConflict());
    }

    @Test
    void cannot_invite_as_owner_role() throws Exception {
        Session owner = onboardOwner("Acme", "owner4@acme.com", PASSWORD);
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "boss@acme.com", "role", "OWNER"))))
                .andExpect(status().isBadRequest());
    }
}
