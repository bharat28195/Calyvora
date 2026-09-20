package com.calyvora.common.web;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rate limiter, switched on.
 *
 * <p>The rest of the suite runs with it off — every test logs in, repeatedly, from one address, and a
 * limit sized for a stranger would fail tests that are behaving correctly. So this is the one class
 * that turns it on, and it uses deliberately tiny limits so the ceiling is reached in a few requests
 * rather than by issuing six hundred of them.
 */
@TestPropertySource(properties = {
        "calyvora.rate-limit.enabled=true",
        "calyvora.rate-limit.auth-per-minute=5",
        "calyvora.rate-limit.api-per-minute=10",
})
class RateLimitIntegrationTest extends IntegrationTestBase {

    private static final String PW = "Passw0rd!x";

    @Autowired
    private RateLimiter limiter;

    /** A distinct address per test, so one test's spent allowance is never another's. */
    private static int addressCounter = 0;

    private static synchronized String freshAddress() {
        return "203.0.113." + (++addressCounter % 250);
    }

    /**
     * The buckets are one map on a context shared by every test in this class, so without this each
     * test would inherit whatever the last one spent and the order they happened to run in would
     * decide whether they passed.
     */
    @BeforeEach
    void clearBuckets() {
        limiter.reset();
    }

    @Test
    @DisplayName("guessing a password is cut off, with a Retry-After saying how long to wait")
    void the_auth_surface_is_held_tight() throws Exception {
        String ip = freshAddress();
        int limit = 5;

        for (int i = 0; i < limit; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "nobody@nowhere.test", "password", "wrong" + i))))
                    .andExpect(status().isUnauthorized());
        }

        MvcResult blocked = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@nowhere.test", "password", "wrong-again"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andReturn();

        // Without this a client is told to back off and given no idea for how long, so it retries
        // immediately and is refused again.
        String retryAfter = blocked.getResponse().getHeader("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(Integer.parseInt(retryAfter)).isGreaterThan(0);
    }

    @Test
    @DisplayName("one address running out does not lock out everybody else")
    void the_limit_is_per_caller() throws Exception {
        String noisy = freshAddress();
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("X-Forwarded-For", noisy)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", "nobody@nowhere.test", "password", "wrong"))));
        }
        // That address is now spent.
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", noisy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@nowhere.test", "password", "wrong"))))
                .andExpect(status().isTooManyRequests());

        // Somebody else, arriving at the same moment, is unaffected — this is the whole reason the key
        // is the forwarded address rather than the load balancer's.
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", freshAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@nowhere.test", "password", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a signed-in user is limited as themselves, not as their office's address")
    void authenticated_callers_are_keyed_by_user() throws Exception {
        // Both sessions created first, then the allowances cleared: onboarding is itself several
        // requests against the auth surface — more than the five this class allows — so the budget is
        // cleared between them as well as before the part being measured.
        Session owner = onboardOwner("Ratco", "admin@ratco.test", PW);
        limiter.reset();
        Session other = onboardOwner("Ratco2", "admin@ratco2.test", PW);
        limiter.reset();

        // Two people behind one corporate NAT: same address, different tokens. Spending one
        // allowance must not touch the other.
        String office = freshAddress();
        int limit = 10;
        for (int i = 0; i < limit; i++) {
            mockMvc.perform(get("/api/v1/people/employees")
                            .header("X-Forwarded-For", office)
                            .header("Authorization", "Bearer " + owner.accessToken()))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/people/employees")
                        .header("X-Forwarded-For", office)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/v1/people/employees")
                        .header("X-Forwarded-For", office)
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("signing in correctly, over and over, is never what gets you cut off")
    void a_correct_password_is_not_a_guess() throws Exception {
        Session owner = onboardOwner("Ratco3", "admin@ratco3.test", PW);
        assertThat(owner).isNotNull();
        limiter.reset();

        // Well past the five-per-minute ceiling. Forty people arriving at nine o'clock from one
        // office look like this, and so does a load test signing in valid users in a burst — neither
        // is an attack, and locking them out for being right is the failure this refund prevents.
        String office = freshAddress();
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", office)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "admin@ratco3.test", "password", PW))))
                    .andExpect(status().isOk());
        }

        // The budget is still there for whoever is actually guessing, from that same address.
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("X-Forwarded-For", office)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", "admin@ratco3.test", "password", "wrong" + i))));
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", office)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@ratco3.test", "password", "wrong-again"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("the remaining allowance is on every response, not only the one that is refused")
    void the_headers_say_what_is_left() throws Exception {
        String ip = freshAddress();
        MvcResult first = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@nowhere.test", "password", "wrong"))))
                .andReturn();

        assertThat(first.getResponse().getHeader("X-RateLimit-Limit")).isEqualTo("5");
        // A client that can see it is running out can slow down before it is cut off, which is the
        // difference between a well-behaved integration and one that discovers the limit by hitting it.
        assertThat(Integer.parseInt(first.getResponse().getHeader("X-RateLimit-Remaining")))
                .isLessThan(5);
    }

    @Test
    @DisplayName("refreshing a session is never rate limited")
    void refresh_is_exempt() throws Exception {
        String ip = freshAddress();
        // Spend the anonymous allowance several times over.
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", "nobody@nowhere.test", "password", "wrong"))));
        }

        // A browser with several tabs renews once per tab on waking. Turning that into a 429 would log
        // somebody out of a product they were using correctly, so refresh is exempt and leans on its
        // own reuse detection instead. 401 here is the absent cookie, not the limiter.
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Forwarded-For", ip))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("health checks are never rate limited — the keep-alive pinger depends on it")
    void health_is_exempt() throws Exception {
        String ip = freshAddress();
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", "nobody@nowhere.test", "password", "wrong"))));
        }
        mockMvc.perform(get("/actuator/health").header("X-Forwarded-For", ip))
                .andExpect(status().isOk());
    }
}
