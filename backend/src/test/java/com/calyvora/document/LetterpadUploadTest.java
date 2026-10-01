package com.calyvora.document;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uploading the company's own letterpad (PD-20 continued).
 *
 * <p>The bytes live in the letterhead row, so the things worth asserting are the ones that keep
 * that from being a mistake: a cap on the size, a refusal of anything that is not an image, and
 * the tenant boundary — one company's stationery must never be reachable from another.
 */
class LetterpadUploadTest extends IntegrationTestBase {

    private static final String PW = "Passw0rd!x";

    /** A one-pixel PNG. Enough to be stored and served; small enough to sit in a test. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    @Test
    @DisplayName("an uploaded letterpad is stored, served back, and switched on by the upload itself")
    void upload_then_serve() throws Exception {
        Session owner = onboardOwner("Padco", "admin@padco.test", PW);

        mockMvc.perform(multipart("/api/v1/documents/letterhead/background")
                        .file(file("letterpad.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasBackground").value(true))
                // Uploading is the act of choosing it; a second step to switch it on would be a
                // step nobody would understand skipping.
                .andExpect(jsonPath("$.useBackground").value(true))
                .andExpect(jsonPath("$.backgroundName").value("letterpad.png"));

        mockMvc.perform(get("/api/v1/documents/letterhead/background")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));

        // The settings JSON must not carry the image: it is read on every letter preview, and a
        // megabyte of base64 on that path would be paid for constantly and used never.
        JsonNode head = getJson("/api/v1/documents/letterhead", owner);
        assertThat(head.has("backgroundImage")).isFalse();
    }

    @Test
    @DisplayName("it can be switched off without deleting it, and on again without re-uploading")
    void the_switch_is_separate_from_the_file() throws Exception {
        Session owner = onboardOwner("Padco2", "admin@padco2.test", PW);
        mockMvc.perform(multipart("/api/v1/documents/letterhead/background")
                        .file(file("pad.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());

        patchLetterhead(owner, false)
                .andExpect(jsonPath("$.useBackground").value(false))
                .andExpect(jsonPath("$.hasBackground").value(true));

        patchLetterhead(owner, true).andExpect(jsonPath("$.useBackground").value(true));
    }

    @Test
    @DisplayName("switching it on with nothing uploaded is refused rather than printing a blank page")
    void cannot_switch_on_without_a_file() throws Exception {
        Session owner = onboardOwner("Padco3", "admin@padco3.test", PW);
        patchLetterhead(owner, true).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a PDF is refused, and the message says what to do instead")
    void only_images() throws Exception {
        Session owner = onboardOwner("Padco4", "admin@padco4.test", PW);
        mockMvc.perform(multipart("/api/v1/documents/letterhead/background")
                        .file(file("letterpad.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PNG")));
    }

    @Test
    @DisplayName("anything over 2 MB is refused")
    void size_is_capped() throws Exception {
        Session owner = onboardOwner("Padco5", "admin@padco5.test", PW);
        byte[] tooBig = new byte[(int) LetterheadService.MAX_BYTES + 1];
        mockMvc.perform(multipart("/api/v1/documents/letterhead/background")
                        .file(file("huge.png", "image/png", tooBig))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2 MB")));
    }

    @Test
    @DisplayName("one company's letterpad is not another's")
    void tenants_are_separate() throws Exception {
        Session mine = onboardOwner("Padco6", "admin@padco6.test", PW);
        mockMvc.perform(multipart("/api/v1/documents/letterhead/background")
                        .file(file("pad.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + mine.accessToken()))
                .andExpect(status().isOk());

        Session theirs = onboardOwner("Rivalco", "admin@rivalco.test", PW);
        mockMvc.perform(get("/api/v1/documents/letterhead/background")
                        .header("Authorization", "Bearer " + theirs.accessToken()))
                .andExpect(status().isNotFound());

        // And removing it is the owner's to do, not a neighbour's — the 404 above is the tenant
        // boundary, so this asserts the other company still has its own after the fact.
        mockMvc.perform(delete("/api/v1/documents/letterhead/background")
                        .header("Authorization", "Bearer " + theirs.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/documents/letterhead/background")
                        .header("Authorization", "Bearer " + mine.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("removing it clears the file and the choice together")
    void remove_clears_both() throws Exception {
        Session owner = onboardOwner("Padco7", "admin@padco7.test", PW);
        mockMvc.perform(multipart("/api/v1/documents/letterhead/background")
                        .file(file("pad.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/documents/letterhead/background")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasBackground").value(false))
                .andExpect(jsonPath("$.useBackground").value(false));

        mockMvc.perform(get("/api/v1/documents/letterhead/background")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions patchLetterhead(Session s, boolean use)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/documents/letterhead")
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json(Map.of("useBackground", use))));
    }
}
