package com.calyvora.work;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sprint depth (V23): story points, capacity, the sprint report, burndown and velocity. */
class SprintReportIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    @Test
    void the_report_adds_up_commitment_progress_and_capacity() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String projectId = project(owner, "Atlas", "ATL");
        String sprintId = sprint(owner, projectId, 20);
        start(owner, sprintId);

        String a = task(owner, projectId, "Ship RS256", 8);
        String b = task(owner, projectId, "Roll out RLS", 5);
        String c = task(owner, projectId, "Polish onboarding", null);   // deliberately unestimated
        for (String id : new String[]{a, b, c}) {
            moveToSprint(owner, id, sprintId);
        }
        setStatus(owner, a, "DONE");

        JsonNode report = getJson("/api/v1/work/sprints/" + sprintId + "/report", owner);
        assertThat(report.get("capacityPoints").asInt()).isEqualTo(20);
        assertThat(report.get("committedPoints").asInt()).isEqualTo(13);   // 8 + 5, the unsized one adds 0
        assertThat(report.get("completedPoints").asInt()).isEqualTo(8);
        assertThat(report.get("remainingPoints").asInt()).isEqualTo(5);
        assertThat(report.get("totalTasks").asInt()).isEqualTo(3);
        assertThat(report.get("doneTasks").asInt()).isEqualTo(1);
        assertThat(report.get("unestimatedTasks").asInt()).isEqualTo(1);   // and it says so
    }

    @Test
    void the_burndown_spans_the_sprint_and_records_today() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String projectId = project(owner, "Atlas", "ATL");
        String sprintId = sprint(owner, projectId, null);
        start(owner, sprintId);
        moveToSprint(owner, task(owner, projectId, "Big one", 10), sprintId);

        JsonNode report = getJson("/api/v1/work/sprints/" + sprintId + "/report", owner);
        JsonNode burndown = report.get("burndown");
        assertThat(burndown.size()).isEqualTo(report.get("daysTotal").asInt());

        // The ideal line runs from the commitment down to zero.
        assertThat(burndown.get(0).get("ideal").asDouble()).isEqualTo(10.0);
        assertThat(burndown.get(burndown.size() - 1).get("ideal").asDouble()).isZero();

        // Today has a recorded figure; days still to come are marked projected and carry no actual.
        boolean today = false;
        for (JsonNode p : burndown) {
            if (LocalDate.now().toString().equals(p.get("date").asText())) {
                today = true;
                assertThat(p.get("remainingPoints").asInt()).isEqualTo(10);
                assertThat(p.get("projected").asBoolean()).isFalse();
            }
            if (p.get("projected").asBoolean()) {
                assertThat(p.get("remainingPoints").isNull()).isTrue();
            }
        }
        assertThat(today).isTrue();
    }

    @Test
    void finishing_work_moves_the_recorded_remaining_down() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String projectId = project(owner, "Atlas", "ATL");
        String sprintId = sprint(owner, projectId, null);
        start(owner, sprintId);
        String taskId = task(owner, projectId, "Ship it", 6);
        moveToSprint(owner, taskId, sprintId);

        assertThat(remainingToday(owner, sprintId)).isEqualTo(6);
        setStatus(owner, taskId, "DONE");
        assertThat(remainingToday(owner, sprintId)).isZero();
    }

    @Test
    void velocity_averages_completed_sprints_only() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String projectId = project(owner, "Atlas", "ATL");

        // Sprint 1: 10 points, all done, completed.
        String first = sprint(owner, projectId, null);
        start(owner, first);
        String t1 = task(owner, projectId, "One", 10);
        moveToSprint(owner, t1, first);
        setStatus(owner, t1, "DONE");
        complete(owner, first);

        // Sprint 2: 6 points done, completed.
        String second = sprint(owner, projectId, null);
        start(owner, second);
        String t2 = task(owner, projectId, "Two", 6);
        moveToSprint(owner, t2, second);
        setStatus(owner, t2, "DONE");
        complete(owner, second);

        // Sprint 3 is still running, so it must not drag the average down.
        String third = sprint(owner, projectId, null);
        start(owner, third);
        moveToSprint(owner, task(owner, projectId, "Three", 99), third);

        JsonNode velocity = getJson("/api/v1/work/projects/" + projectId + "/velocity", owner);
        assertThat(velocity.get("sprints").size()).isEqualTo(2);
        assertThat(velocity.get("averageVelocity").asDouble()).isEqualTo(8.0);   // (10 + 6) / 2
        assertThat(velocity.get("suggestedCommitment").asInt()).isEqualTo(8);
    }

    @Test
    void the_report_shows_who_is_carrying_what() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");

        String projectId = getJson("/api/v1/work/projects", owner).get(0).get("id").asText();
        String sprintId = getJson("/api/v1/work/projects/" + projectId + "/sprints", owner)
                .get(0).get("id").asText();

        JsonNode report = getJson("/api/v1/work/sprints/" + sprintId + "/report", owner);
        assertThat(report.get("byAssignee").size()).isPositive();
        assertThat(report.get("byAssignee").get(0).get("name").asText()).isNotBlank();
        // Sorted heaviest-first, so an overloaded person is the first thing you see.
        int first = report.get("byAssignee").get(0).get("points").asInt();
        for (JsonNode load : report.get("byAssignee")) {
            assertThat(load.get("points").asInt()).isLessThanOrEqualTo(first);
        }
    }

    @Test
    void a_silly_estimate_is_capped_and_a_negative_one_clears_it() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String projectId = project(owner, "Atlas", "ATL");
        String taskId = task(owner, projectId, "Typo estimate", 99999);

        assertThat(patchTask(owner, taskId, Map.of()).get("storyPoints").asInt()).isEqualTo(200);
        assertThat(patchTask(owner, taskId, Map.of("storyPoints", -1)).get("storyPoints").isNull()).isTrue();
    }

    // ---- helpers ----

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private String project(Session s, String name, String key) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/work/projects").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "key", key))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private String sprint(Session s, String projectId, Integer capacity) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Sprint " + System.nanoTime());
        body.put("startDate", LocalDate.now().minusDays(3).toString());
        body.put("endDate", LocalDate.now().plusDays(7).toString());
        if (capacity != null) body.put("capacityPoints", capacity);
        MvcResult res = mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/sprints")
                        .header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void start(Session s, String sprintId) throws Exception {
        mockMvc.perform(post("/api/v1/work/sprints/" + sprintId + "/start").header("Authorization", bearer(s)))
                .andExpect(status().isOk());
    }

    private void complete(Session s, String sprintId) throws Exception {
        mockMvc.perform(post("/api/v1/work/sprints/" + sprintId + "/complete").header("Authorization", bearer(s)))
                .andExpect(status().isOk());
    }

    private String task(Session s, String projectId, String title, Integer points) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        if (points != null) body.put("storyPoints", points);
        MvcResult res = mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode patchTask(Session s, String taskId, Map<String, ?> body) throws Exception {
        MvcResult res = mockMvc.perform(patch("/api/v1/work/tasks/" + taskId).header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private void moveToSprint(Session s, String taskId, String sprintId) throws Exception {
        patchTask(s, taskId, Map.of("sprintId", sprintId));
    }

    private void setStatus(Session s, String taskId, String status) throws Exception {
        patchTask(s, taskId, Map.of("status", status));
    }

    private int remainingToday(Session s, String sprintId) throws Exception {
        JsonNode report = getJson("/api/v1/work/sprints/" + sprintId + "/report", s);
        for (JsonNode p : report.get("burndown")) {
            if (LocalDate.now().toString().equals(p.get("date").asText())) {
                return p.get("remainingPoints").asInt();
            }
        }
        throw new AssertionError("no snapshot recorded for today");
    }
}
