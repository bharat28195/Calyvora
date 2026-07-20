package com.calyvora.work;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkSprintsIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private String createProject(Session s, String name, String key) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/work/projects").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", name, "key", key))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTask(Session s, String projectId, String title) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/tasks").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", title))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String createSprint(Session s, String projectId, String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/sprints").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", name, "goal", "Ship it"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private void assignTaskToSprint(Session s, String taskId, String sprintId) throws Exception {
        mockMvc.perform(patch("/api/v1/work/tasks/" + taskId).header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("sprintId", sprintId))))
                .andExpect(status().isOk());
    }

    @Test
    void new_tasks_land_in_the_backlog_and_the_board_shows_them_until_a_sprint_is_active() throws Exception {
        Session owner = onboardOwner("Acme", "s1@acme.com", PW);
        String projectId = createProject(owner, "Platform", "PLT");
        createTask(owner, projectId, "First task");
        createTask(owner, projectId, "Second task");

        // both are in the backlog...
        assertThat(getJson("/api/v1/work/projects/" + projectId + "/backlog", owner).size()).isEqualTo(2);
        // ...and, with no active sprint, they show on the board (board.activeSprint is null)
        JsonNode board = getJson("/api/v1/work/projects/" + projectId + "/board", owner);
        assertThat(board.get("activeSprint").isNull()).isTrue();
        assertThat(board.get("tasks").size()).isEqualTo(2);
    }

    @Test
    void sprint_lifecycle_moves_tasks_onto_the_board_and_carries_leftovers_back() throws Exception {
        Session owner = onboardOwner("Acme", "s2@acme.com", PW);
        String projectId = createProject(owner, "Platform", "PLT");
        String t1 = createTask(owner, projectId, "Done task");
        String t2 = createTask(owner, projectId, "Unfinished task");
        String sprintId = createSprint(owner, projectId, "Sprint 1");

        // plan both tasks into the sprint; they leave the backlog
        assignTaskToSprint(owner, t1, sprintId);
        assignTaskToSprint(owner, t2, sprintId);
        assertThat(getJson("/api/v1/work/projects/" + projectId + "/backlog", owner).size()).isZero();

        // start the sprint → it becomes the board
        mockMvc.perform(post("/api/v1/work/sprints/" + sprintId + "/start").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        JsonNode board = getJson("/api/v1/work/projects/" + projectId + "/board", owner);
        assertThat(board.get("activeSprint").get("id").asText()).isEqualTo(sprintId);
        assertThat(board.get("tasks").size()).isEqualTo(2);

        // finish one task; complete the sprint → the unfinished one returns to the backlog, the done one does not
        mockMvc.perform(patch("/api/v1/work/tasks/" + t1).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work/sprints/" + sprintId + "/complete").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        JsonNode backlog = getJson("/api/v1/work/projects/" + projectId + "/backlog", owner);
        assertThat(backlog.size()).isEqualTo(1);
        assertThat(backlog.get(0).get("title").asText()).isEqualTo("Unfinished task");
    }

    @Test
    void a_project_can_have_only_one_active_sprint() throws Exception {
        Session owner = onboardOwner("Acme", "s3@acme.com", PW);
        String projectId = createProject(owner, "Platform", "PLT");
        String a = createSprint(owner, projectId, "Sprint A");
        String b = createSprint(owner, projectId, "Sprint B");

        mockMvc.perform(post("/api/v1/work/sprints/" + a + "/start").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        // starting a second one while A is active conflicts
        mockMvc.perform(post("/api/v1/work/sprints/" + b + "/start").header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    void tickets_get_a_ref_an_assignee_and_a_status() throws Exception {
        Session owner = onboardOwner("Acme", "s4@acme.com", PW);
        String projectId = createProject(owner, "Platform", "PLT");
        String myEmp = getJson("/api/v1/people/me", owner).get("id").asText();

        MvcResult r = mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/tickets").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("subject", "Login is broken", "requesterName", "Jane Customer",
                                "priority", "HIGH", "assigneeId", myEmp))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ref").value("PLT-T1"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.assigneeName").value("Test Owner"))
                .andReturn();
        String ticketId = objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/v1/work/tickets/" + ticketId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "RESOLVED"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));

        assertThat(getJson("/api/v1/work/projects/" + projectId + "/tickets", owner).size()).isEqualTo(1);
    }

    @Test
    void sprints_and_tickets_are_isolated_across_tenants() throws Exception {
        Session ownerA = onboardOwner("Company A", "sa@a.com", PW);
        Session ownerB = onboardOwner("Company B", "sb@b.com", PW);
        String projectA = createProject(ownerA, "A Project", "AAA");
        String sprintA = createSprint(ownerA, projectA, "A Sprint");

        // B cannot list A's sprints/backlog/board, nor start A's sprint, nor add a ticket to A's project
        mockMvc.perform(post("/api/v1/work/sprints/" + sprintA + "/start").header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/work/sprints/" + sprintA).header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/work/projects/" + projectA + "/tickets").header("Authorization", bearer(ownerB))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("subject", "intrude"))))
                .andExpect(status().isNotFound());
        // B cannot even read A's project backlog/sprints (project is invisible → 404)
        mockMvc.perform(get("/api/v1/work/projects/" + projectA + "/backlog").header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/work/projects/" + projectA + "/sprints").header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());
        // and B's own project list is unaffected
        assertThat(getJson("/api/v1/work/projects", ownerB).size()).isZero();
    }
}
