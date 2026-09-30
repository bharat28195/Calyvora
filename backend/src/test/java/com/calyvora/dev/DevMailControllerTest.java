package com.calyvora.dev;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The mail smoke test must stay diagnostic: with no provider configured (as in tests, and as on a
 * freshly deployed host) it has to report the failure as data rather than blow up with a 500 — that
 * is the entire reason it exists, since the real send path swallows errors silently.
 *
 * <p>It sends a real email through the configured provider, so it is restricted to the platform
 * owner: an anonymous send endpoint on a deployment with a live Resend key is a way for anyone to
 * send mail on the vendor's account to any address. These tests authenticate as the platform owner.
 */
class DevMailControllerTest extends IntegrationTestBase {

    /**
     * Nothing is configured here, so the transport is the console one — which cannot fail, because
     * it only writes to the log. Reporting that as {@code sent} would be precisely the false
     * reassurance this endpoint exists to prevent, so an undelivered message must read as undelivered.
     */
    @Test
    void reports_a_failure_when_no_mail_provider_is_configured() throws Exception {
        Session platform = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        mockMvc.perform(post("/api/v1/dev/test-email").param("to", "someone@example.com")
                        .header("Authorization", "Bearer " + platform.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(false))
                .andExpect(jsonPath("$.provider").value("CONSOLE"))
                .andExpect(jsonPath("$.error").isNotEmpty())
                // The echoed settings are what make a misconfiguration obvious at a glance.
                .andExpect(jsonPath("$.config.endpoint").isNotEmpty())
                .andExpect(jsonPath("$.config.from").isNotEmpty());
    }

    @Test
    void never_echoes_the_credential() throws Exception {
        Session platform = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        mockMvc.perform(post("/api/v1/dev/test-email").param("to", "someone@example.com")
                        .header("Authorization", "Bearer " + platform.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.password").doesNotExist())
                .andExpect(jsonPath("$.config.apiKey").doesNotExist());
    }

    @Test
    void the_send_endpoint_is_closed_to_anonymous_callers() throws Exception {
        // The point of the guard: a stranger cannot send mail on the deployment's account. 403, not
        // a delivered email, when no platform-owner session is presented.
        mockMvc.perform(post("/api/v1/dev/test-email").param("to", "someone@example.com"))
                .andExpect(status().isForbidden());
    }
}
