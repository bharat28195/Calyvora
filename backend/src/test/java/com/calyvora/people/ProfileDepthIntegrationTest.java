package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Richer employee profile (feedback C.2): skills, rating, end date, and "what they're working on". */
class ProfileDepthIntegrationTest extends IntegrationTestBase {

    private Session demo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login("ava.chen@northwind.demo", "demopass123");
    }

    private JsonNode employeeByEmail(Session s, String email) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", s)) {
            if (e.get("email").asText().equals(email)) return e;
        }
        throw new AssertionError("no employee " + email);
    }

    @Test
    void seeded_profile_carries_skills_and_rating() throws Exception {
        Session owner = demo();
        JsonNode marcus = employeeByEmail(owner, "marcus.reed@northwind.demo");
        List<String> skills = objectMapper.convertValue(marcus.get("skills"), List.class);
        assertThat(skills).contains("Java");
        assertThat(marcus.get("rating").asInt()).isEqualTo(5);
    }

    @Test
    void working_on_lists_assigned_open_tasks() throws Exception {
        Session owner = demo();
        String marcusId = employeeByEmail(owner, "marcus.reed@northwind.demo").get("id").asText();
        JsonNode work = getJson("/api/v1/people/employees/" + marcusId + "/work", owner);
        assertThat(work.size()).isGreaterThan(0);
        assertThat(work.get(0).get("ref").asText()).startsWith("ATL-");
    }

    @Test
    void admin_can_update_skills_rating_and_end_date() throws Exception {
        Session owner = demo();
        String id = employeeByEmail(owner, "leo.martins@northwind.demo").get("id").asText();
        mockMvc.perform(patch("/api/v1/people/employees/" + id)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("skills", List.of("UX", "Illustration"), "rating", 3, "endDate", "2027-01-01"))))
                .andExpect(status().isOk());
        JsonNode leo = employeeByEmail(owner, "leo.martins@northwind.demo");
        assertThat(objectMapper.convertValue(leo.get("skills"), List.class)).containsExactly("UX", "Illustration");
        assertThat(leo.get("rating").asInt()).isEqualTo(3);
        assertThat(leo.get("endDate").asText()).isEqualTo("2027-01-01");
    }
}
