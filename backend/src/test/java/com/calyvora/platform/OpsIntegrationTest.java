package com.calyvora.platform;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.calyvora.common.web.SlowRequestFilter;
import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observability (backlog 3.3): who a log line belongs to, which requests were slow, and where the
 * per-endpoint numbers can be read.
 */
// Zero: every request is "slow", so the WARN line can be asserted without a sleep.
@TestPropertySource(properties = "calyvora.ops.slow-request-ms=0")
class OpsIntegrationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Test
    @DisplayName("the platform owner can read p95 per endpoint; a company admin cannot")
    void endpoint_stats_are_platform_only() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session ava = login("ava.chen@northwind.demo", PW);
        getJson("/api/v1/people/employees", ava);   // something to have been timed

        mockMvc.perform(get("/api/v1/platform/ops/endpoints").header("Authorization", "Bearer " + ava.accessToken()))
                .andExpect(status().isForbidden());

        Session vendor = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        JsonNode stats = getJson("/api/v1/platform/ops/endpoints?top=100", vendor);

        JsonNode employees = null;
        for (JsonNode row : stats) {
            if ("/api/v1/people/employees".equals(row.get("uri").asText()) && "GET".equals(row.get("method").asText())) {
                employees = row;
            }
        }
        assertThat(employees).as("the endpoint that was just called is in the table").isNotNull();
        assertThat(employees.get("count").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(employees.get("p95Ms").asDouble()).isGreaterThan(0);
        assertThat(employees.get("maxMs").asDouble()).isGreaterThan(0);

        // Deliberately NOT asserting p95 <= max. The two numbers come from different places: max is
        // the true observed maximum, while p95 is estimated from Micrometer's histogram buckets and
        // rounds up to a bucket boundary — so the estimate can legitimately sit above the largest
        // sample, and does whenever the two fall either side of a boundary. This test asserted the
        // ordering for two weeks and passed on luck; it failed the first time a run landed 160.3ms
        // of max against a 165.7ms bucket edge. What is worth pinning is that both are populated.
        assertThat(employees.get("meanMs").asDouble())
                .as("the mean is a real average of real samples, so it cannot exceed the maximum")
                .isLessThanOrEqualTo(employees.get("maxMs").asDouble());
    }

    @Test
    @DisplayName("a slow request is logged with the tenant and user that made it")
    void slow_requests_carry_the_tenant() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session ava = login("ava.chen@northwind.demo", PW);
        String meId = getJson("/api/v1/auth/me", ava).get("user").get("id").asText();
        String companyId = getJson("/api/v1/auth/me", ava).get("company").get("id").asText();

        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        Logger slow = (Logger) LoggerFactory.getLogger(SlowRequestFilter.class);
        slow.addAppender(captured);
        try {
            getJson("/api/v1/people/employees", ava);
        } finally {
            slow.detachAppender(captured);
        }

        ILoggingEvent line = captured.list.stream()
                .filter(e -> e.getFormattedMessage().contains("GET /api/v1/people/employees"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no slow-request line for the call"));
        // The MDC is what makes the line useful: it is how a warning gets tied to a customer.
        assertThat(line.getMDCPropertyMap()).containsEntry("companyId", companyId);
        assertThat(line.getMDCPropertyMap()).containsEntry("userId", meId);
        assertThat(line.getMDCPropertyMap()).containsKey("correlationId");
        assertThat(line.getFormattedMessage()).contains("-> 200 in");
    }
}
