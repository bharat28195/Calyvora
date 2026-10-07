package com.calyvora.auth;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A new company's first admin (V67): Orbit picks a temporary password, emails it, and asks for a new
 * one at first sign-in — and anyone can change their own password from inside the app.
 */
class WelcomeAndPasswordChangeTest extends IntegrationTestBase {

    private Session platform() throws Exception {
        platformOwner.ensurePlatformOwner();
        return login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
    }

    private JsonNode createCompany(Session platform, String email, String password) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("companyName", "Newco", "adminFirstName", "Nia",
                "adminLastName", "Admin", "adminEmail", email, "seats", 5, "months", 6));
        if (password != null) body.put("password", password);
        MvcResult r = mockMvc.perform(post("/api/v1/platform/companies")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private MvcResult changePassword(Session s, String current, String next, int expected) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", current, "newPassword", next))))
                .andExpect(status().is(expected))
                .andReturn();
    }

    @Test
    void a_blank_password_is_generated_emailed_and_must_be_changed() throws Exception {
        JsonNode created = createCompany(platform(), "nia@newco.test", null);
        assertThat(created.get("welcomeEmailSent").asBoolean()).isTrue();
        assertThat(created.get("temporaryPassword").isNull())
                .as("delivered by email, so the console never sees it").isTrue();

        var welcome = email().welcomes().get(email().welcomes().size() - 1);
        assertThat(welcome.to()).isEqualTo("nia@newco.test");
        String temporary = welcome.url();
        assertThat(temporary).matches("[A-Za-z2-9]{4}-[A-Za-z2-9]{4}-[A-Za-z2-9]{4}");

        Session admin = login("nia@newco.test", temporary);
        assertThat(getJson("/api/v1/auth/me", admin).get("user").get("mustChangePassword").asBoolean()).isTrue();

        MvcResult changed = changePassword(admin, temporary, "MyOwnPassw0rd", 200);
        JsonNode body = objectMapper.readTree(changed.getResponse().getContentAsString());
        assertThat(body.get("me").get("user").get("mustChangePassword").asBoolean()).isFalse();
        assertThat(changed.getResponse().getHeader("Set-Cookie")).as("a fresh session for this device").isNotBlank();

        // The temporary password no longer works; the new one does.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nia@newco.test", "password", temporary))))
                .andExpect(status().isUnauthorized());
        Session again = login("nia@newco.test", "MyOwnPassw0rd");
        assertThat(getJson("/api/v1/auth/me", again).get("user").get("mustChangePassword").asBoolean()).isFalse();
    }

    @Test
    void a_password_the_owner_typed_is_still_a_temporary_one() throws Exception {
        createCompany(platform(), "typed@newco.test", "OwnerChose123");
        Session admin = login("typed@newco.test", "OwnerChose123");
        assertThat(getJson("/api/v1/auth/me", admin).get("user").get("mustChangePassword").asBoolean()).isTrue();
    }

    @Test
    void the_current_password_must_be_right_and_the_new_one_strong() throws Exception {
        createCompany(platform(), "rules@newco.test", "OwnerChose123");
        Session admin = login("rules@newco.test", "OwnerChose123");

        changePassword(admin, "wrong-password", "MyOwnPassw0rd", 400);
        changePassword(admin, "OwnerChose123", "short1", 400);
        changePassword(admin, "OwnerChose123", "nodigitsatall", 400);
        changePassword(admin, "OwnerChose123", "OwnerChose123", 400);
        changePassword(admin, "OwnerChose123", "MyOwnPassw0rd", 200);
    }

    @Test
    void changing_your_password_needs_you_to_be_signed_in() throws Exception {
        mockMvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "x", "newPassword", "MyOwnPassw0rd"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void existing_accounts_are_not_asked_to_change_anything() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");
        assertThat(getJson("/api/v1/auth/me", owner).get("user").get("mustChangePassword").asBoolean()).isFalse();
    }
}
