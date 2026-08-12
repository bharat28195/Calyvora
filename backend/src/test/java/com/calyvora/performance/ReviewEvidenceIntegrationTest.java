package com.calyvora.performance;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Evidence-based reviews (PD-22, D1): the review is pre-filled with what the person actually
 * shipped during the cycle's period, rather than what anyone can remember.
 *
 * <p>These tests pin the three things that make the feature trustworthy: the period boundary is
 * real, reopened work stops counting, and the evidence is exactly as private as the review.
 */
class ReviewEvidenceIntegrationTest extends IntegrationTestBase {

    private static final String DEMO_PW = "demopass123";

    @Test
    void delivered_work_becomes_review_evidence() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        String cycleId = createCycle(owner, "Evidence Cycle", "2026-01-01", "2026-12-31");
        JsonNode priya = reviewFor(owner, cycleId, "Priya Nair");
        String reviewId = priya.get("id").asText();
        String employeeId = priya.get("employeeId").asText();

        String projectId = createProject(owner, "Evidence Project", "EVD");
        String taskId = createTask(owner, projectId, "Ship the audit log", employeeId, 5);

        // The demo tenant already has delivered work (and the V43 backfill dates it), so measure the
        // change this task makes rather than assuming a clean slate.
        JsonNode before = evidence(owner, reviewId);
        int deliveredBefore = before.get("work").get("tasksDelivered").asInt();
        int pointsBefore = before.get("work").get("storyPointsDelivered").asInt();
        assertThat(titles(before)).doesNotContain("Ship the audit log");

        finishTask(owner, taskId);

        JsonNode after = evidence(owner, reviewId);
        assertThat(after.get("empty").asBoolean()).isFalse();
        assertThat(after.get("work").get("tasksDelivered").asInt()).isEqualTo(deliveredBefore + 1);
        assertThat(after.get("work").get("storyPointsDelivered").asInt()).isEqualTo(pointsBefore + 5);
        assertThat(after.get("periodStart").asText()).isEqualTo("2026-01-01");
        assertThat(after.get("employeeName").asText()).isEqualTo("Priya Nair");

        JsonNode item = itemTitled(after, "Ship the audit log");
        assertThat(item.get("ref").asText()).startsWith("EVD-");
        assertThat(item.get("projectName").asText()).isEqualTo("Evidence Project");
        assertThat(item.get("link").asText()).isEqualTo("/work/" + projectId);
        assertThat(item.get("finishedOn").isNull()).isFalse();
    }

    @Test
    void work_finished_outside_the_period_is_not_evidence_for_it() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);

        // A cycle covering a period that has already ended. Work completed *now* belongs to a later
        // review, not this one — the whole point of stamping completion rather than trusting updatedAt.
        String pastCycle = createCycle(owner, "Last Year", "2025-01-01", "2025-12-31");
        JsonNode priya = reviewFor(owner, pastCycle, "Priya Nair");
        String employeeId = priya.get("employeeId").asText();

        String projectId = createProject(owner, "Late Project", "LATE");
        finishTask(owner, createTask(owner, projectId, "Done this year", employeeId, 3));

        JsonNode ev = evidence(owner, priya.get("id").asText());
        assertThat(ev.get("empty").asBoolean()).isTrue();
        assertThat(ev.get("work").get("tasksDelivered").asInt()).isZero();
    }

    @Test
    void reopening_a_task_withdraws_it_from_the_evidence() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        String cycleId = createCycle(owner, "Reopen Cycle", "2026-01-01", "2026-12-31");
        JsonNode priya = reviewFor(owner, cycleId, "Priya Nair");
        String reviewId = priya.get("id").asText();

        String projectId = createProject(owner, "Reopen Project", "REO");
        String taskId = createTask(owner, projectId, "Thought we were done", priya.get("employeeId").asText(), 2);

        int deliveredBefore = evidence(owner, reviewId).get("work").get("tasksDelivered").asInt();

        finishTask(owner, taskId);
        JsonNode done = evidence(owner, reviewId);
        assertThat(done.get("work").get("tasksDelivered").asInt()).isEqualTo(deliveredBefore + 1);
        assertThat(titles(done)).contains("Thought we were done");

        // Back to in-progress: it is no longer something they delivered, and its points go with it.
        patchJson("/api/v1/work/tasks/" + taskId, owner, Map.of("status", "IN_PROGRESS"));
        JsonNode ev = evidence(owner, reviewId);
        assertThat(ev.get("work").get("tasksDelivered").asInt()).isEqualTo(deliveredBefore);
        assertThat(titles(ev)).doesNotContain("Thought we were done");
    }

    /**
     * The period is a pair of dates; completion is an instant. Converting between them on the
     * server's clock instead of the company's puts hours of work on the wrong side of the boundary —
     * for an IST tenant, the first five and a half hours of the opening day fall outside their own
     * review. Attendance learned this in PD-16; this pins it for evidence too.
     *
     * <p>The zone is chosen at runtime so the company's calendar date is always a different day from
     * UTC's — otherwise the assertion would pass under the bug for part of the day.
     */
    @Test
    void the_period_is_read_on_the_companys_clock_not_the_servers() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);

        // Kiritimati (+14) is already tomorrow late in the UTC day; Niue (-11) is still yesterday
        // early in it. The ranges overlap, so one of them always differs from the UTC date.
        String zoneId = Instant.now().atZone(ZoneOffset.UTC).getHour() >= 10
                ? "Pacific/Kiritimati" : "Pacific/Niue";
        ZoneId zone = ZoneId.of(zoneId);
        LocalDate companyToday = LocalDate.now(zone);
        assertThat(companyToday).isNotEqualTo(LocalDate.now(ZoneOffset.UTC));   // the test must discriminate

        patchJson("/api/v1/company/settings", owner, Map.of(
                "timezone", zoneId, "locale", "en", "currency", "USD"));

        // A cycle covering exactly the company's today — work finished right now belongs to it.
        String cycleId = createCycle(owner, "Clock Cycle", companyToday.toString(), companyToday.toString());
        JsonNode priya = reviewFor(owner, cycleId, "Priya Nair");

        String projectId = createProject(owner, "Clock Project", "CLK");
        finishTask(owner, createTask(owner, projectId, "Finished just now", priya.get("employeeId").asText(), 1));

        JsonNode ev = evidence(owner, priya.get("id").asText());
        assertThat(titles(ev)).contains("Finished just now");
        // …and it is dated on the company's calendar, not the server's.
        assertThat(itemTitled(ev, "Finished just now").get("finishedOn").asText())
                .isEqualTo(companyToday.toString());
    }

    @Test
    void evidence_is_as_private_as_the_review_it_belongs_to() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        String cycleId = createCycle(owner, "Privacy Cycle", "2026-01-01", "2026-12-31");
        String priyaReview = reviewFor(owner, cycleId, "Priya Nair").get("id").asText();

        // The employee may see their own evidence — they answer to it in the self-assessment.
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        assertThat(evidence(priya, priyaReview).get("employeeName").asText()).isEqualTo("Priya Nair");

        // Her reporting manager may see it, because he writes the review.
        Session marcus = login("marcus.reed@northwind.demo", DEMO_PW);
        assertThat(evidence(marcus, priyaReview).get("employeeName").asText()).isEqualTo("Priya Nair");

        // A colleague who is neither may not — same rule as GET /reviews/{id}.
        Session leo = login("leo.martins@northwind.demo", DEMO_PW);
        mockMvc.perform(get("/api/v1/performance/reviews/" + priyaReview + "/evidence")
                        .header("Authorization", bearer(leo)))
                .andExpect(status().isForbidden());
    }

    @Test
    void another_tenant_cannot_read_the_evidence() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        String cycleId = createCycle(owner, "Isolation Cycle", "2026-01-01", "2026-12-31");
        String priyaReview = reviewFor(owner, cycleId, "Priya Nair").get("id").asText();

        Session outsider = onboardOwner("Rival Co", "rival@rival.test", "RivalPass123!");
        mockMvc.perform(get("/api/v1/performance/reviews/" + priyaReview + "/evidence")
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    // ---- helpers ----

    private void seedDemo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private JsonNode evidence(Session s, String reviewId) throws Exception {
        return getJson("/api/v1/performance/reviews/" + reviewId + "/evidence", s);
    }

    private String createCycle(Session owner, String name, String start, String end) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/performance/cycles").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "periodStart", start, "periodEnd", end))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode reviewFor(Session owner, String cycleId, String employeeName) throws Exception {
        for (JsonNode r : getJson("/api/v1/performance/cycles/" + cycleId + "/reviews", owner)) {
            if (employeeName.equals(r.get("employeeName").asText())) return r;
        }
        throw new AssertionError("No review for " + employeeName);
    }

    private String createProject(Session s, String name, String key) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/work/projects").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "key", key))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTask(Session s, String projectId, String title, String assigneeId, int points)
            throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(s)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", title, "assigneeId", assigneeId, "storyPoints", points))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private void finishTask(Session s, String taskId) throws Exception {
        patchJson("/api/v1/work/tasks/" + taskId, s, Map.of("status", "DONE"));
    }

    /** Titles of the delivered tasks in an evidence pack. */
    private static List<String> titles(JsonNode evidence) {
        List<String> out = new ArrayList<>();
        evidence.get("work").get("tasks").forEach(t -> out.add(t.get("title").asText()));
        return out;
    }

    private static JsonNode itemTitled(JsonNode evidence, String title) {
        for (JsonNode t : evidence.get("work").get("tasks")) {
            if (title.equals(t.get("title").asText())) return t;
        }
        throw new AssertionError("No delivered task titled " + title);
    }

    private void patchJson(String path, Session s, Map<String, ?> body) throws Exception {
        mockMvc.perform(patch(path).header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }
}
