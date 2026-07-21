package com.calyvora.dev;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one-click demo seed provisions a populated, self-consistent company that logs in and reads
 * back across all three apps — the whole point being that a client demo never opens onto empty screens.
 */
class DemoSeedIntegrationTest extends IntegrationTestBase {

    @Test
    void seed_provisions_a_full_demo_company_that_logs_in_and_reads_across_apps() throws Exception {
        MvcResult seeded = mockMvc.perform(post("/api/v1/dev/seed-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Northwind Robotics"))
                .andExpect(jsonPath("$.email").value("ava.chen@northwind.demo"))
                .andExpect(jsonPath("$.alreadySeeded").value(false))
                .andReturn();

        JsonNode creds = objectMapper.readTree(seeded.getResponse().getContentAsString());
        Session owner = login(creds.get("email").asText(), creds.get("password").asText());

        // People: 6 employees with real profiles.
        assertThat(getJson("/api/v1/people/employees", owner).size()).isEqualTo(6);
        assertThat(getJson("/api/v1/people/departments", owner).size()).isEqualTo(4);

        // Work: the Atlas project exists with an active sprint and tasks.
        JsonNode projects = getJson("/api/v1/work/projects", owner);
        assertThat(projects.size()).isEqualTo(1);
        assertThat(projects.get(0).get("key").asText()).isEqualTo("ATL");

        // Knowledge: the handbook space with pages, one linking a Work task.
        JsonNode spaces = getJson("/api/v1/knowledge/spaces", owner);
        assertThat(spaces.size()).isEqualTo(1);
        assertThat(spaces.get(0).get("key").asText()).isEqualTo("ENG");
    }

    @Test
    void seed_is_idempotent() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/dev/seed-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadySeeded").value(true));
    }
}
