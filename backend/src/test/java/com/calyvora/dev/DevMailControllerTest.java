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
 */
class DevMailControllerTest extends IntegrationTestBase {

    /**
     * Nothing is configured here, so the transport is the console one — which cannot fail, because
     * it only writes to the log. Reporting that as {@code sent} would be precisely the false
     * reassurance this endpoint exists to prevent, so an undelivered message must read as undelivered.
     */
    @Test
    void reports_a_failure_when_no_mail_provider_is_configured() throws Exception {
        mockMvc.perform(post("/api/v1/dev/test-email").param("to", "someone@example.com"))
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
        // This endpoint is unauthenticated — it must not become a way to read secrets back out.
        mockMvc.perform(post("/api/v1/dev/test-email").param("to", "someone@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.password").doesNotExist())
                .andExpect(jsonPath("$.config.apiKey").doesNotExist());
    }
}
