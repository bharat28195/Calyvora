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

    /**
     * Company documents: a publisher writes, everyone reads what is published, and a draft is invisible
     * to anyone who cannot publish it — including to search.
     */
    @Test
    void publishers_write_everyone_reads_published_pages() throws Exception {
        Session owner = onboardOwner("Acme", "owner3@acme.com", PW);
        Session dev = addMember(owner, "dev@acme.com");
        String spaceId = createSpace(owner, "Handbook", "HB");

        String pageId = createPage(owner, spaceId, Map.of("title", "Vacation policy", "body", "Everyone gets 25 unicorn days."));
        assertThat(getJson("/api/v1/knowledge/pages/mine", owner).size()).isEqualTo(1);

        // A draft: the member cannot see it in the folder, open it, or find it.
        assertThat(getJson("/api/v1/knowledge/spaces/" + spaceId + "/pages", dev).size()).isZero();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/knowledge/pages/" + pageId).header("Authorization", bearer(dev)))
                .andExpect(status().isNotFound());
        assertThat(getJson("/api/v1/knowledge/search?q=unicorn", dev).size()).isZero();

        // Published: now everyone reads it, and search finds it with a snippet.
        mockMvc.perform(patch("/api/v1/knowledge/pages/" + pageId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isOk());
        assertThat(getJson("/api/v1/knowledge/spaces/" + spaceId + "/pages", dev).size()).isEqualTo(1);
        JsonNode hits = getJson("/api/v1/knowledge/search?q=unicorn", dev);
        assertThat(hits.size()).isEqualTo(1);
        assertThat(hits.get(0).get("snippet").asText()).contains("unicorn");
    }

    @Test
    void a_member_cannot_create_edit_or_delete_company_documents() throws Exception {
        Session owner = onboardOwner("Acme", "owner6@acme.com", PW);
        Session dev = addMember(owner, "dev6@acme.com");
        String spaceId = createSpace(owner, "Handbook", "HB");
        String pageId = createPage(owner, spaceId, Map.of("title", "Leave policy", "body", "21 days."));

        mockMvc.perform(post("/api/v1/knowledge/spaces").header("Authorization", bearer(dev))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Mine", "key", "MINE"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/knowledge/spaces/" + spaceId + "/pages").header("Authorization", bearer(dev))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "My own policy"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/knowledge/pages/" + pageId).header("Authorization", bearer(dev))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", "100 days."))))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/knowledge/pages/" + pageId).header("Authorization", bearer(dev)))
                .andExpect(status().isForbidden());
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
