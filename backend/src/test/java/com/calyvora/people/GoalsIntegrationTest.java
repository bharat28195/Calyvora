package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Employee goals (feedback C8): seeded goals, progress→achieved, and self-vs-others permissions. */
class GoalsIntegrationTest extends IntegrationTestBase {

    private Session demo(String email) throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login(email, "demopass123");
    }

    private String employeeId(Session s, String email) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", s)) {
            if (e.get("email").asText().equals(email)) return e.get("id").asText();
        }
        throw new AssertionError("no employee " + email);
    }

    @Test
    void seeded_goals_are_returned() throws Exception {
        Session owner = demo("ava.chen@northwind.demo");
        String marcus = employeeId(owner, "marcus.reed@northwind.demo");
        JsonNode goals = getJson("/api/v1/people/employees/" + marcus + "/goals", owner);
        assertThat(goals.size()).isGreaterThan(0);
        assertThat(goals.get(0).get("progress").asInt()).isEqualTo(80);
    }

    @Test
    void progress_100_marks_a_goal_achieved() throws Exception {
        Session owner = demo("ava.chen@northwind.demo");
        String leo = employeeId(owner, "leo.martins@northwind.demo");
        MvcResult created = mockMvc.perform(post("/api/v1/people/employees/" + leo + "/goals")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Redesign onboarding"))))
                .andExpect(status().isCreated())
                .andReturn();
        String goalId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/v1/people/employees/" + leo + "/goals/" + goalId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("progress", 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPathStatus("ACHIEVED"));
    }

    @Test
    void member_manages_own_goals_but_not_others() throws Exception {
        Session owner = demo("ava.chen@northwind.demo");
        String priya = employeeId(owner, "priya.nair@northwind.demo");
        String marcus = employeeId(owner, "marcus.reed@northwind.demo");
        Session priyaSession = login("priya.nair@northwind.demo", "demopass123"); // MEMBER

        // own goal → allowed
        mockMvc.perform(post("/api/v1/people/employees/" + priya + "/goals")
                        .header("Authorization", "Bearer " + priyaSession.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Learn Rust"))))
                .andExpect(status().isCreated());

        // someone else's goal → forbidden
        mockMvc.perform(post("/api/v1/people/employees/" + marcus + "/goals")
                        .header("Authorization", "Bearer " + priyaSession.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Not my goal"))))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.ResultMatcher jsonPathStatus(String value) {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(value);
    }
}
