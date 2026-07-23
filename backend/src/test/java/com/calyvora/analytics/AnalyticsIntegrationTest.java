package com.calyvora.analytics;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Company analytics: real cross-app figures from the demo, and Owner/Admin-only access. */
class AnalyticsIntegrationTest extends IntegrationTestBase {

    private static final String DEMO_PW = "demopass123";

    @Test
    void overview_reports_real_cross_app_numbers() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);

        JsonNode o = getJson("/api/v1/analytics/overview", owner);

        // People: the demo has 6 active users; headcount growth is a 12-month series ending at 6.
        assertThat(o.get("people").get("headcount").asInt()).isEqualTo(6);
        assertThat(o.get("people").get("headcountGrowth")).hasSize(12);
        assertThat(o.get("people").get("byDepartment").size()).isGreaterThan(0);

        // Work: task status series has all three states and a running sprint with committed points.
        assertThat(o.get("work").get("tasksByStatus")).hasSize(3);
        assertThat(o.get("work").get("activeSprint").get("committed").asInt()).isGreaterThan(0);

        // Finance: the demo seeds claims, so at least one category has spend.
        double catTotal = 0;
        for (JsonNode s : o.get("finance").get("byCategory")) catTotal += s.get("value").asDouble();
        assertThat(catTotal).isGreaterThan(0);
    }

    @Test
    void members_cannot_see_company_analytics() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + priya.accessToken()))
                .andExpect(status().isForbidden());
    }
}
