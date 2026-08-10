package com.calyvora.auth;

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
 * Creating a workspace should let you straight into it — you've just set up a company and need to
 * start adding people, not wait on an email.
 *
 * <p>The second test here is the important one. Registration used to assign {@code OWNER}, the role
 * that unlocks the platform console — which reads <em>every</em> tenant's companies, headcount and
 * billing. Nothing exploited it only because verification was broken and no self-registered account
 * could log in; switching that gate off without this fix would have handed the console to every
 * signup. A signup is an ADMIN of their own company and nothing more.
 */
class SignupActivationIntegrationTest extends IntegrationTestBase {

    private static final String PW = "workspace1234";

    @Test
    void a_new_workspace_can_log_in_immediately() throws Exception {
        register("Acme Logistics", "founder@acme-signup.test");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "founder@acme-signup.test", "password", PW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void a_new_signup_cannot_reach_the_platform_console() throws Exception {
        register("Rival Logistics", "founder@rival-signup.test");
        Session founder = login("founder@rival-signup.test", PW);

        mockMvc.perform(get("/api/v1/platform/companies")
                        .header("Authorization", "Bearer " + founder.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void a_new_signup_is_an_admin_of_their_own_company() throws Exception {
        register("Northgate Foods", "founder@northgate-signup.test");
        Session founder = login("founder@northgate-signup.test", PW);

        JsonNode me = getJson("/api/v1/auth/me", founder);

        assertThat(me.get("user").get("role").asText()).isEqualTo("ADMIN");
        assertThat(me.get("company").get("name").asText()).isEqualTo("Northgate Foods");
    }

    @Test
    void a_new_admin_can_immediately_invite_members() throws Exception {
        // The whole point of signing in straight away: set the company up.
        register("Bright Labs", "founder@bright-signup.test");
        Session founder = login("founder@bright-signup.test", PW);

        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + founder.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "colleague@bright-signup.test", "role", "MEMBER"))))
                .andExpect(status().isCreated());
    }

    @Test
    void the_seeded_platform_owner_still_reaches_the_console() throws Exception {
        // The lock must not have been tightened so far that the person it's for is shut out.
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);

        mockMvc.perform(get("/api/v1/platform/companies")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void one_companys_admin_still_cannot_see_another_companys_people() throws Exception {
        register("Alpha Co", "founder@alpha-signup.test");
        register("Beta Co", "founder@beta-signup.test");
        Session alpha = login("founder@alpha-signup.test", PW);

        JsonNode directory = getJson("/api/v1/people/employees", alpha);

        assertThat(directory).hasSize(1);
        assertThat(directory.get(0).get("email").asText()).isEqualTo("founder@alpha-signup.test");
    }

    private void register(String company, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", company, "firstName", "Test",
                                "lastName", "Founder", "email", email, "password", PW))))
                .andExpect(status().isCreated());
    }
}
