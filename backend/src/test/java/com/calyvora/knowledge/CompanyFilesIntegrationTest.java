package com.calyvora.knowledge;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Files in the company documents area: a publisher uploads, everyone in the company lists and
 * downloads, nobody else can — and the bytes come back exactly as they went in.
 */
class CompanyFilesIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";
    private static final byte[] POLICY_PDF = "%PDF-1.4 leave policy, 21 days".getBytes(StandardCharsets.UTF_8);

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
                        .content(json(Map.of("token", token, "firstName", "Mia", "lastName", "Member", "password", PW))))
                .andExpect(status().isOk());
        return login(email, PW);
    }

    private String createSpace(Session s, String key) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/knowledge/spaces").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Handbook " + key, "key", key))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", POLICY_PDF);
    }

    private String upload(Session s, String spaceId, MockMultipartFile file) throws Exception {
        MvcResult r = mockMvc.perform(multipart("/api/v1/knowledge/spaces/" + spaceId + "/files")
                        .file(file).param("title", "Leave policy").header("Authorization", bearer(s)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void a_publisher_uploads_and_everyone_in_the_company_reads_it() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session member = addMember(owner, "mia@acme.com");
        String spaceId = createSpace(owner, "HB");
        String fileId = upload(owner, spaceId, pdf("leave-policy.pdf"));

        JsonNode listed = getJson("/api/v1/knowledge/spaces/" + spaceId + "/files", member);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("title").asText()).isEqualTo("Leave policy");
        assertThat(listed.get(0).get("fileName").asText()).isEqualTo("leave-policy.pdf");
        assertThat(listed.get(0).get("sizeBytes").asLong()).isEqualTo(POLICY_PDF.length);

        MvcResult download = mockMvc.perform(get("/api/v1/knowledge/files/" + fileId + "/download")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(POLICY_PDF);
        assertThat(download.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
    }

    @Test
    void a_member_can_neither_upload_nor_delete() throws Exception {
        Session owner = onboardOwner("Acme", "owner2@acme.com", PW);
        Session member = addMember(owner, "mia2@acme.com");
        String spaceId = createSpace(owner, "HB");
        String fileId = upload(owner, spaceId, pdf("policy.pdf"));

        mockMvc.perform(multipart("/api/v1/knowledge/spaces/" + spaceId + "/files")
                        .file(pdf("mine.pdf")).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/knowledge/files/" + fileId).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/knowledge/files/" + fileId).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        assertThat(getJson("/api/v1/knowledge/spaces/" + spaceId + "/files", member)).isEmpty();
    }

    /** Anything a browser might run (HTML, SVG, scripts) is refused at the door, not just served safely. */
    @Test
    void only_document_types_are_accepted() throws Exception {
        Session owner = onboardOwner("Acme", "owner3@acme.com", PW);
        String spaceId = createSpace(owner, "HB");
        MockMultipartFile html = new MockMultipartFile("file", "policy.html", "text/html",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/knowledge/spaces/" + spaceId + "/files")
                        .file(html).header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void another_company_cannot_list_or_download() throws Exception {
        Session ownerA = onboardOwner("Company A", "a@a.com", PW);
        Session ownerB = onboardOwner("Company B", "b@b.com", PW);
        String spaceA = createSpace(ownerA, "AAA");
        String fileA = upload(ownerA, spaceA, pdf("salaries.pdf"));

        mockMvc.perform(get("/api/v1/knowledge/spaces/" + spaceA + "/files").header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/knowledge/files/" + fileA + "/download").header("Authorization", bearer(ownerB)))
                .andExpect(status().isNotFound());
    }
}
