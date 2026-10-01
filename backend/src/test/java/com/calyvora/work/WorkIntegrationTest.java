package com.calyvora.work;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private Session addMember(Session owner, String email) throws Exception {
        mockMvc.perform(post("/api/v1/invitations").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", "MEMBER"))))
                .andExpect(status().isCreated());
        String token = email().lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "Dev", "lastName", "Eloper", "password", PW))))
                .andExpect(status().isOk());
        return login(email, PW);
    }

    private String createProject(Session s, String name, String key) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/work/projects").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "key", key))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTask(Session s, String projectId, String title) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/tasks").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", title))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String myEmployeeId(Session s) throws Exception {
        return getJson("/api/v1/people/me", s).get("id").asText();
    }

    @Test
    void project_and_task_board_lifecycle() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String projectId = createProject(owner, "Engineering", "eng");

        // key is normalized to upper-case; task refs use it
        JsonNode projects = getJson("/api/v1/work/projects", owner);
        assertThat(projects.size()).isEqualTo(1);
        assertThat(projects.get(0).get("key").asText()).isEqualTo("ENG");

        // two tasks get sequential numbers ENG-1, ENG-2
        mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/tasks").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "Set up CI"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ref").value("ENG-1"))
                .andExpect(jsonPath("$.status").value("TODO"));
        String t2 = createTask(owner, projectId, "Write docs");

        // move ENG-2 across the board TODO -> IN_PROGRESS -> DONE
        mockMvc.perform(patch("/api/v1/work/tasks/" + t2).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "IN_PROGRESS", "priority", "HIGH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"));

        JsonNode tasks = getJson("/api/v1/work/projects/" + projectId + "/tasks", owner);
        assertThat(tasks.size()).isEqualTo(2);

        // project shows 2 open tasks; completing one drops it to 1
        assertThat(getJson("/api/v1/work/projects", owner).get(0).get("openTaskCount").asInt()).isEqualTo(2);
        mockMvc.perform(patch("/api/v1/work/tasks/" + t2).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk());
        assertThat(getJson("/api/v1/work/projects", owner).get(0).get("openTaskCount").asInt()).isEqualTo(1);
    }

    @Test
    void tasks_can_be_assigned_to_people_and_show_up_in_my_work() throws Exception {
        Session owner = onboardOwner("Acme", "owner2@acme.com", PW);
        Session dev = addMember(owner, "dev@acme.com");
        String devEmpId = myEmployeeId(dev);
        String projectId = createProject(owner, "Platform", "PLT");
        String taskId = createTask(owner, projectId, "Ship the thing");

        // assign the task to the developer (cross-app link into People OS)
        mockMvc.perform(patch("/api/v1/work/tasks/" + taskId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("assigneeId", devEmpId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(devEmpId))
                .andExpect(jsonPath("$.assigneeName").value("Dev Eloper"));

        // it appears in the developer's "My work"
        JsonNode mine = getJson("/api/v1/work/tasks/mine", dev);
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).get("title").asText()).isEqualTo("Ship the thing");
    }

    @Test
    void duplicate_project_key_conflicts() throws Exception {
        Session owner = onboardOwner("Acme", "owner3@acme.com", PW);
        createProject(owner, "Engineering", "ENG");
        mockMvc.perform(post("/api/v1/work/projects").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Eng 2", "key", "eng"))))
                .andExpect(status().isConflict());
    }

    @Test
    void member_cannot_archive_a_project() throws Exception {
        Session owner = onboardOwner("Acme", "owner4@acme.com", PW);
        Session dev = addMember(owner, "dev4@acme.com");
        String projectId = createProject(owner, "Engineering", "ENG");
        mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/archive").header("Authorization", bearer(dev)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/archive").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void projects_and_tasks_are_isolated_across_tenants() throws Exception {
        Session ownerA = onboardOwner("Company A", "a@a.com", PW);
        Session ownerB = onboardOwner("Company B", "b@b.com", PW);
        String projectA = createProject(ownerA, "A Project", "AAA");
        createTask(ownerA, projectA, "secret task");

        // B sees no projects, cannot read A's project, cannot add tasks to it
        assertThat(getJson("/api/v1/work/projects", ownerB).size()).isZero();
        mockMvc.perform(post("/api/v1/work/projects/" + projectA + "/tasks").header("Authorization", bearer(ownerB))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "intrude"))))
                .andExpect(status().isNotFound());
    }
}
