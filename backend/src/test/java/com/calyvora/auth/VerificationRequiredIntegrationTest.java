package com.calyvora.auth;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Email verification was switched off by default (PD-13) so a new workspace is usable immediately —
 * but it was made a switch rather than deleted, because it is the right behaviour again once
 * outgoing mail is proven. This covers that switch being on, so the path can't quietly rot.
 */
@TestPropertySource(properties = "calyvora.security.verification.required=true")
class VerificationRequiredIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "password1234";

    @Test
    void login_before_verification_is_forbidden_when_verification_is_required() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Gated Co", "firstName", "Ada", "lastName", "L",
                                "email", "gated@acme.test", "password", PASSWORD))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "gated@acme.test", "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Please verify your email before logging in"));
    }

    @Test
    void verifying_the_email_then_lets_them_in() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Gated Two", "firstName", "Ada", "lastName", "L",
                                "email", "gated2@acme.test", "password", PASSWORD))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", email.lastVerificationToken()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "gated2@acme.test", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}
