package com.calyvora.dev;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The SMTP smoke test must stay diagnostic: with no mail server reachable (as in tests, and as on a
 * freshly deployed host) it has to report the failure as data rather than blow up with a 500 — that
 * is the entire reason it exists, since the real send path swallows errors silently.
 */
class DevMailControllerTest extends IntegrationTestBase {

    @Test
    void reports_the_failure_instead_of_throwing_when_smtp_is_unreachable() throws Exception {
        mockMvc.perform(post("/api/v1/dev/test-email").param("to", "someone@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty())
                // The echoed settings are what make a misconfiguration obvious at a glance.
                .andExpect(jsonPath("$.config.host").isNotEmpty())
                .andExpect(jsonPath("$.config.from").isNotEmpty());
    }

    @Test
    void never_echoes_the_password() throws Exception {
        mockMvc.perform(post("/api/v1/dev/test-email").param("to", "someone@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.password").doesNotExist());
    }
}
