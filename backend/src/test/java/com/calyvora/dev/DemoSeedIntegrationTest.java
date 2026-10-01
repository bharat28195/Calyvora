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

        // People: 7 employees with real profiles — the seventh is the intern under Priya, which is
        // what gives the demo a third level of reporting and therefore a "My team" for a MEMBER.
        assertThat(getJson("/api/v1/people/employees", owner).size()).isEqualTo(7);
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

    /**
     * The top-up path — the only path a demo company that already exists ever takes.
     *
     * <p>Anything added to the fresh-seed path after a company was first created never reaches that
     * company, because {@code seed()} returns early for it. Not hypothetical: the deployed demo had
     * been seeded long before designations existed, so its Designations screen was empty and nobody in
     * its org chart was a MEMBER with reports — precisely the thing PD-32 is about, missing from the
     * environment used to demonstrate it. Re-seeding has to repair that, and be safe to run again.
     */
    @Test
    void re_seeding_tops_up_the_third_reporting_level_and_the_ladder_without_duplicating_them()
            throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");

        // Twice more: the top-up has to be repeatable, not merely correct once.
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());

        assertThat(getJson("/api/v1/people/employees", owner).size())
                .as("the intern is added once, not once per call")
                .isEqualTo(7);
        assertThat(getJson("/api/v1/designations", owner).size())
                .as("the ladder is created once, not appended to on every call")
                .isEqualTo(4);

        // Priya is a plain MEMBER and leads a team because somebody reports to her — the whole claim.
        Session priya = login("priya.nair@northwind.demo", "demopass123");
        JsonNode standing = getJson("/api/v1/team/mine", priya);
        assertThat(standing.get("leadsTeam").asBoolean()).isTrue();
        assertThat(standing.get("directCount").asInt()).isEqualTo(1);
    }
}
