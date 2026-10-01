package com.calyvora.email;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verification has to be demonstrable on a deployment with no mail provider — which is every demo
 * and staging environment. The link is captured and served from {@code /api/v1/dev/mailbox}, so the
 * round trip (register → open the link → verified) works with nothing configured.
 *
 * <p>Only the endpoint's existence and reachability are asserted here: the test context replaces the
 * whole {@link EmailService} with a recording double, so the capture itself is covered where the real
 * service is exercised, in {@link DispatchingEmailServiceTest}.
 */
class MailCaptureIntegrationTest extends IntegrationTestBase {

    @Test
    void the_mailbox_endpoint_is_served_outside_prod() throws Exception {
        // It used to be registered only under the `embedded` profile, so a staging deployment — the
        // one place a demo actually happens — answered 404 and the link was unreachable.
        mockMvc.perform(get("/api/v1/dev/mailbox")).andExpect(status().isOk());
    }

    @Test
    void the_mailbox_is_readable_without_signing_in() throws Exception {
        // Deliberate: the person who needs the verification link is the one who cannot log in yet.
        mockMvc.perform(get("/api/v1/dev/mailbox")).andExpect(status().isOk());
    }
}
