package com.calyvora.common.error;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A URL that matches no handler used to fall through to the catch-all and come back as
 * {@code 500 Something went wrong} — indistinguishable from a genuine server fault. That costs real
 * time to debug and actively misleads anyone integrating against the API, so a typo must read as 404.
 */
class NotFoundRoutingTest extends IntegrationTestBase {

    /**
     * An anonymous caller is turned away before routing happens, so an unknown path is 401 rather
     * than 404 — deliberate, since answering 404 here would let a stranger map which endpoints exist.
     * What matters is that it is not the old opaque 500.
     */
    @Test
    void an_unknown_route_is_not_a_500_for_an_anonymous_caller() throws Exception {
        mockMvc.perform(get("/api/v1/this-does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void a_known_route_with_the_wrong_verb_is_405_not_500() throws Exception {
        // /auth/login exists but only answers POST.
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknown_routes_still_404_for_an_authenticated_caller() throws Exception {
        // Guard against the fix depending on the anonymous path — the 500 was seen while logged in.
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");

        mockMvc.perform(get("/api/v1/people/not-a-real-endpoint")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNotFound());
    }
}
