package com.calyvora.knowledge;

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

class KnowledgeIntegrationTest extends IntegrationTestBase {

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

    private String createSpace(Session s, String name, String key) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/knowledge/spaces").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "key", key))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String createPage(Session s, String spaceId, Map<String, Object> body) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/knowledge/spaces/" + spaceId + "/pages").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    // Work OS helpers, so we can prove the doc<->task cross-app link.
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

    @Test
    void space_and_page_lifecycle() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String spaceId = createSpace(owner, "Engineering", "eng");

        // key normalized to upper-case; space starts with zero pages
        JsonNode spaces = getJson("/api/v1/knowledge/spaces", owner);
        assertThat(spaces.size()).isEqualTo(1);
        assertThat(spaces.get(0).get("key").asText()).isEqualTo("ENG");
        assertThat(spaces.get(0).get("pageCount").asInt()).isZero();

        // create a page — author auto-set to the creator (a People employee), starts as DRAFT
        String pageId = createPage(owner, spaceId, Map.of("title", "Runbook", "body", "# Deploy steps\nRun the pipeline."));
        mockMvc.perform(patch("/api/v1/knowledge/pages/" + pageId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.authorName").value("Test Owner"));

        // it shows up in the space tree and bumps the space page count
        assertThat(getJson("/api/v1/knowledge/spaces/" + spaceId + "/pages", owner).size()).isEqualTo(1);
        assertThat(getJson("/api/v1/knowledge/spaces", owner).get(0).get("pageCount").asInt()).isEqualTo(1);
    }

    @Test
    void page_links_to_a_work_task_cross_app() throws Exception {
        Session owner = onboardOwner("Acme", "owner2@acme.com", PW);
        String projectId = createProject(owner, "Platform", "PLT");
        String taskId = createTask(owner, projectId, "Ship the thing");
        String spaceId = createSpace(owner, "Docs", "DOC");

        // create a page linked to the Work task (doc<->task), then read it back with the resolved ref
        String pageId = createPage(owner, spaceId,
                Map.of("title", "Ship notes", "body", "How we shipped it.", "linkedTaskId", taskId));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/knowledge/pages/" + pageId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedTaskId").value(taskId))
                .andExpect(jsonPath("$.linkedTaskRef").value("PLT-1"));
    }

    @Test
    void authored_pages_show_up_in_my_pages_and_search() throws Exception {
        Session owner = onboardOwner("Acme", "owner3@acme.com", PW);
        Session dev = addMember(owner, "dev@acme.com");
        String spaceId = createSpace(owner, "Handbook", "HB");

        // the developer authors a page
        createPage(dev, spaceId, Map.of("title", "Vacation policy", "body", "Everyone gets 25 unicorn days."));

        // "my pages" for the dev returns it, authored by Dev Eloper
        JsonNode mine = getJson("/api/v1/knowledge/pages/mine", dev);
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).get("authorName").asText()).isEqualTo("Dev Eloper");

        // full-text search across the tenant finds it with a snippet
        JsonNode hits = getJson("/api/v1/knowledge/search?q=unicorn", owner);
        assertThat(hits.size()).isEqualTo(1);
        assertThat(hits.get(0).get("title").asText()).isEqualTo("Vacation policy");
        assertThat(hits.get(0).get("snippet").asText()).contains("unicorn");

        // the owner has authored nothing → their "my pages" is empty
        assertThat(getJson("/api/v1/knowledge/pages/mine", owner).size()).isZero();
    }

    @Test
    void duplicate_space_key_conflicts() throws Exception {
        Session owner = onboardOwner("Acme", "owner4@acme.com", PW);
        createSpace(owner, "Engineering", "ENG");
        mockMvc.perform(post("/api/v1/knowledge/spaces").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Eng 2", "key", "eng"))))
                .andExpect(status().isConflict());
    }

    @Test
    void member_cannot_archive_a_space() throws Exception {
        Session owner = onboardOwner("Acme", "owner5@acme.com", PW);
        Session dev = addMember(owner, "dev5@acme.com");
        String spaceId = createSpace(owner, "Engineering", "ENG");
        mockMvc.perform(post("/api/v1/knowledge/spaces/" + spaceId + "/archive").header("Authorization", bearer(dev)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/knowledge/spaces/" + spaceId + "/archive").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void spaces_and_pages_are_isolated_across_tenants() throws Exception {
        Session ownerA = onboardOwner("Company A", "a@a.com", PW);
        Session ownerB = onboardOwner("Company B", "b@b.com", PW);
        String spaceA = createSpace(ownerA, "A Space", "AAA");
        String pageA = createPage(ownerA, spaceA, Map.of("title", "secret doc", "body", "top secret"));

        // B sees no spaces, cannot read A's space, cannot add pages to it, cannot read A's page
        assertThat(getJson("/api/v1/knowledge/spaces", ownerB).size()).isZero();
        mockMvc.perform(post("/api/v1/knowledge/spaces/" + spaceA + "/pages").header("Authorization", bearer(ownerB))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "intrude"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/knowledge/pages/" + pageA).header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());

        // B's tenant-wide search cannot see A's secret page
        assertThat(getJson("/api/v1/knowledge/search?q=secret", ownerB).size()).isZero();
    }
}
