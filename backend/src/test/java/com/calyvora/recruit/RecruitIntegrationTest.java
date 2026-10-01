package com.calyvora.recruit;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recruitment / ATS: jobs, candidate pipeline, stage moves, and RBAC. */
class RecruitIntegrationTest extends IntegrationTestBase {

    private static final String DEMO_PW = "demopass123";

    @Test
    void the_demo_seeds_openings_with_a_populated_pipeline() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);

        JsonNode jobs = getJson("/api/v1/recruit/jobs", owner);
        assertThat(jobs.size()).isEqualTo(2);

        // The engineering role has candidates spread across stages, including at least one hired on Design.
        int totalCandidates = 0, totalHired = 0;
        for (JsonNode j : jobs) {
            totalCandidates += j.get("candidateCount").asInt();
            totalHired += j.get("hiredCount").asInt();
        }
        assertThat(totalCandidates).isGreaterThanOrEqualTo(8);
        assertThat(totalHired).isGreaterThanOrEqualTo(1);
    }

    @Test
    void a_candidate_moves_through_the_pipeline_and_hire_count_follows() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);

        String jobId = postJson(owner, "/api/v1/recruit/jobs",
                Map.of("title", "QA Engineer", "positions", 1)).get("id").asText();
        String candId = postJson(owner, "/api/v1/recruit/jobs/" + jobId + "/candidates",
                Map.of("name", "Test Candidate", "email", "t@example.com")).get("id").asText();

        // Move to HIRED → the job's hiredCount reflects it.
        postJson(owner, "/api/v1/recruit/candidates/" + candId + "/move", Map.of("stage", "HIRED"));
        JsonNode job = getJson("/api/v1/recruit/jobs/" + jobId, owner);
        assertThat(job.get("candidateCount").asInt()).isEqualTo(1);
        assertThat(job.get("hiredCount").asInt()).isEqualTo(1);
    }

    @Test
    void recruitment_is_admin_only() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        mockMvc.perform(get("/api/v1/recruit/jobs").header("Authorization", bearer(priya)))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private void seedDemo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private JsonNode postJson(Session s, String path, Map<String, ?> body) throws Exception {
        MvcResult res = mockMvc.perform(post(path).header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is2xxSuccessful()).andReturn();
        String content = res.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }
}
