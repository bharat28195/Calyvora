package com.calyvora.performance;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Review cycles: the self → manager → approve flow, hike-to-comp, and the relationship-based RBAC. */
class PerformanceReviewIntegrationTest extends IntegrationTestBase {

    private static final String DEMO_PW = "demopass123";

    @Test
    void self_then_manager_then_approve_applies_the_raise() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);

        String cycleId = createCycle(owner, "Test Cycle");
        JsonNode priya = reviewFor(owner, cycleId, "Priya Nair");
        String reviewId = priya.get("id").asText();
        String employeeId = priya.get("employeeId").asText();
        double before = priya.get("currentSalary").asDouble();   // 145000 in the demo

        // Priya writes and submits her self-assessment → hands off to her manager.
        Session priyaS = login("priya.nair@northwind.demo", DEMO_PW);
        patchJson("/api/v1/performance/reviews/" + reviewId + "/self", priyaS,
                Map.of("selfAssessment", "Shipped the security work.", "submit", true));
        assertThat(reviewById(owner, reviewId).get("status").asText()).isEqualTo("PENDING_MANAGER");

        // Marcus (her manager) rates her and recommends a 10% hike, then submits.
        Session marcus = login("marcus.reed@northwind.demo", DEMO_PW);
        patchJson("/api/v1/performance/reviews/" + reviewId + "/manager", marcus,
                Map.of("rating", 5, "summary", "Excellent", "hikeType", "PERCENT",
                        "hikePercent", 10, "submit", true));
        assertThat(reviewById(owner, reviewId).get("status").asText()).isEqualTo("SUBMITTED");

        // Owner approves → the raise lands in compensation and Priya is told.
        mockMvc.perform(post("/api/v1/performance/reviews/" + reviewId + "/approve")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        assertThat(reviewById(owner, reviewId).get("status").asText()).isEqualTo("APPROVED");

        JsonNode comp = getJson("/api/v1/people/employees/" + employeeId + "/compensation", owner);
        assertThat(comp.get("currentAnnual").asDouble()).isEqualTo(Math.round(before * 1.10 * 100) / 100.0);
        assertThat(comp.get("history").get(0).get("changeType").asText()).isEqualTo("HIKE");
        assertThat(comp.get("history").get(0).get("reason").asText()).contains("Test Cycle");

        assertThat(titles(getJson("/api/v1/notifications", priyaS)))
                .anyMatch(t -> t.contains("approved"));
    }

    @Test
    void only_the_manager_or_an_admin_writes_the_managers_side() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        String cycleId = createCycle(owner, "RBAC Cycle");
        String leoReview = reviewFor(owner, cycleId, "Leo Martins").get("id").asText();

        // Priya isn't Leo's manager and isn't an admin → she can't write his review, nor his self-assessment.
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        mockMvc.perform(patch("/api/v1/performance/reviews/" + leoReview + "/manager")
                        .header("Authorization", bearer(priya)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("rating", 1, "submit", false))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/performance/reviews/" + leoReview + "/self")
                        .header("Authorization", bearer(priya)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("selfAssessment", "not mine", "submit", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void members_cannot_open_or_list_cycles() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);

        mockMvc.perform(get("/api/v1/performance/cycles").header("Authorization", bearer(priya)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/performance/cycles").header("Authorization", bearer(priya))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Sneaky", "periodStart", "2026-01-01", "periodEnd", "2026-12-31"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void a_member_manager_can_set_goals_for_a_report_but_not_a_stranger() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        // Reuse a cycle only to resolve employee ids by name.
        String cycleId = createCycle(owner, "Ids");
        String saraEmp = reviewFor(owner, cycleId, "Sara Okoro").get("employeeId").asText();
        String leoEmp = reviewFor(owner, cycleId, "Leo Martins").get("employeeId").asText();

        // Tom is a plain MEMBER who manages Sara → he may add her a goal (the founder-requested fix).
        Session tom = login("tom.becker@northwind.demo", DEMO_PW);
        mockMvc.perform(post("/api/v1/people/employees/" + saraEmp + "/goals")
                        .header("Authorization", bearer(tom)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Cut first-response time"))))
                .andExpect(status().isCreated());

        // But Tom doesn't manage Leo → forbidden.
        mockMvc.perform(post("/api/v1/people/employees/" + leoEmp + "/goals")
                        .header("Authorization", bearer(tom)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Not your report"))))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private void seedDemo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private String createCycle(Session owner, String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/performance/cycles").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "periodStart", "2026-01-01", "periodEnd", "2026-12-31"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode reviewFor(Session owner, String cycleId, String employeeName) throws Exception {
        JsonNode reviews = getJson("/api/v1/performance/cycles/" + cycleId + "/reviews", owner);
        for (JsonNode r : reviews) {
            if (employeeName.equals(r.get("employeeName").asText())) return r;
        }
        throw new AssertionError("No review for " + employeeName);
    }

    private JsonNode reviewById(Session s, String reviewId) throws Exception {
        return getJson("/api/v1/performance/reviews/" + reviewId, s);
    }

    private void patchJson(String path, Session s, Map<String, ?> body) throws Exception {
        mockMvc.perform(patch(path).header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }

    private static List<String> titles(JsonNode notifications) {
        List<String> out = new ArrayList<>();
        notifications.forEach(n -> out.add(n.get("title").asText()));
        return out;
    }
}
