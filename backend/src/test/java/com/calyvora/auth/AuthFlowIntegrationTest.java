package com.calyvora.auth;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "password1234";

    @Test
    void register_creates_company_and_owner_and_sends_verification() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Acme Inc.", "firstName", "Ada",
                                "lastName", "Lovelace", "email", "ada@acme.com", "password", PASSWORD))))
                .andExpect(status().isCreated());

        assertThat(email.verifications()).hasSize(1);
        assertThat(email.verifications().get(0).to()).isEqualTo("ada@acme.com");
    }

    @Test
    void register_with_duplicate_email_conflicts() throws Exception {
        onboardOwner("Acme", "dup@acme.com", PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Other", "firstName", "X", "lastName", "Y",
                                "email", "dup@acme.com", "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void register_with_weak_password_is_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Acme", "firstName", "Ada", "lastName", "L",
                                "email", "weak@acme.com", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * Verification is off by default (PD-13): creating a workspace means walking into it. The
     * forbidden-until-verified path still exists and is covered by
     * {@link VerificationRequiredIntegrationTest}, which turns the flag on.
     */
    @Test
    void login_without_verification_is_allowed_by_default() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Acme", "firstName", "Ada", "lastName", "L",
                                "email", "pending@acme.com", "password", PASSWORD))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "pending@acme.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void verify_then_login_yields_access_token_and_refresh_cookie() throws Exception {
        Session session = onboardOwner("Acme", "ok@acme.com", PASSWORD);
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();

        getJson("/api/v1/auth/me", session);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("ok@acme.com"))
                // ADMIN, not OWNER: OWNER is the platform vendor above every tenant, and handing it
                // to a signup gave them the owner console over every customer (PD-13 / V35).
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.company.status").value("ACTIVE"));
    }

    @Test
    void wrong_password_is_unauthorized_and_generic() throws Exception {
        onboardOwner("Acme", "real@acme.com", PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "real@acme.com", "password", "wrongpassword1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void unknown_email_and_wrong_password_return_identical_message() throws Exception {
        onboardOwner("Acme", "known@acme.com", PASSWORD);

        String unknownMsg = messageFor("nobody@acme.com", "whatever1234");
        String wrongMsg = messageFor("known@acme.com", "wrongpassword1");
        assertThat(unknownMsg).isEqualTo(wrongMsg); // no user enumeration
    }

    @Test
    void verifying_the_same_token_twice_fails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Acme", "firstName", "Ada", "lastName", "L",
                                "email", "twice@acme.com", "password", PASSWORD))))
                .andExpect(status().isCreated());
        String token = email.lastVerificationToken();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isGone()); // TOKEN_EXPIRED
    }

    @Test
    void refresh_rotates_and_reusing_an_old_token_revokes_the_family() throws Exception {
        Session session = onboardOwner("Acme", "rot@acme.com", PASSWORD);

        // First refresh rotates R1 -> R2
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("calyvora_rt", session.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("calyvora_rt"))
                .andReturn();
        String r2 = refreshCookieValue(refreshed);
        assertThat(r2).isNotEqualTo(session.refreshToken());

        // Reusing the now-rotated R1 is detected → 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("calyvora_rt", session.refreshToken())))
                .andExpect(status().isUnauthorized());

        // ...and the family is burned, so R2 no longer works either
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("calyvora_rt", r2)))
                .andExpect(status().isUnauthorized());
    }

    private String messageFor(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("message").asText();
    }

    private String refreshCookieValue(MvcResult result) {
        jakarta.servlet.http.Cookie cookie = result.getResponse().getCookie("calyvora_rt");
        return cookie == null ? null : cookie.getValue();
    }
}
