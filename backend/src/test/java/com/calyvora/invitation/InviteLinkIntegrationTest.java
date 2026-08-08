package com.calyvora.invitation;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adding a colleague must not depend on email being deliverable.
 *
 * <p>The joining link used to exist only inside the invitation email, so a blocked SMTP port meant an
 * invited person could never get in — with no way for the admin to help them. The link now comes back
 * to the admin too, and can be reissued later, so mail is a convenience rather than the only door.
 */
class InviteLinkIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    @Test
    void creating_an_invitation_returns_the_joining_link() throws Exception {
        Session admin = onboardOwner("Acme", "admin@acme.test", PW);

        JsonNode invite = invite(admin, "newbie@acme.test");

        assertThat(invite.get("acceptUrl").asText())
                .as("the admin needs the link in case the email never arrives")
                .contains("/accept-invite?token=");
    }

    @Test
    void the_returned_link_actually_works() throws Exception {
        // The whole point: someone can join using only what the admin can copy out of the UI.
        Session admin = onboardOwner("Acme", "admin2@acme.test", PW);
        String token = tokenFrom(invite(admin, "joiner@acme.test").get("acceptUrl").asText());

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "New", "lastName", "Joiner",
                                "password", PW))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "joiner@acme.test", "password", PW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void an_admin_can_reissue_a_link_and_the_old_one_stops_working() throws Exception {
        // Tokens are stored hashed, so a lost link can only be replaced. Replacing must invalidate
        // the old one — otherwise a link sent to the wrong address would stay usable forever.
        Session admin = onboardOwner("Acme", "admin3@acme.test", PW);
        JsonNode invite = invite(admin, "reissue@acme.test");
        String originalToken = tokenFrom(invite.get("acceptUrl").asText());

        JsonNode reissued = objectMapper.readTree(mockMvc.perform(
                        post("/api/v1/invitations/" + invite.get("id").asText() + "/link")
                                .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String freshToken = tokenFrom(reissued.get("acceptUrl").asText());

        assertThat(freshToken).isNotEqualTo(originalToken);
        mockMvc.perform(get("/api/v1/invitations/preview").param("token", originalToken))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/invitations/preview").param("token", freshToken))
                .andExpect(status().isOk());
    }

    @Test
    void listing_invitations_never_exposes_a_link() throws Exception {
        // The stored token is a hash, so there is nothing to return — and a link that leaked through
        // a list endpoint would be a way to join a company you were never invited to.
        Session admin = onboardOwner("Acme", "admin4@acme.test", PW);
        invite(admin, "listed@acme.test");

        JsonNode list = getJson("/api/v1/invitations", admin);

        assertThat(list.get(0).get("acceptUrl").isNull()).isTrue();
    }

    @Test
    void a_member_cannot_reissue_an_invitation_link() throws Exception {
        Session admin = onboardOwner("Acme", "admin5@acme.test", PW);
        JsonNode invite = invite(admin, "member@acme.test");
        String token = tokenFrom(invite.get("acceptUrl").asText());
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "Reg", "lastName", "Ular",
                                "password", PW))))
                .andExpect(status().isOk());
        Session member = login("member@acme.test", PW);

        JsonNode second = invite(admin, "another@acme.test");
        mockMvc.perform(post("/api/v1/invitations/" + second.get("id").asText() + "/link")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isForbidden());
    }

    private JsonNode invite(Session admin, String email) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", "MEMBER"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private static String tokenFrom(String url) {
        return url.substring(url.indexOf("token=") + "token=".length());
    }
}
