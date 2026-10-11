package com.calyvora.document;

import com.calyvora.support.IntegrationTestBase;
import com.calyvora.support.RecordingEmailService;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signatures on letters (PD-71) and letters accepted with a signature (PD-72): the company's
 * signatory and seal print where the letter is signed, an employee signs inside Orbit, a candidate
 * through a link, and the signed PDF carries the evidence.
 */
class LetterSigningTest extends IntegrationTestBase {

    @Autowired
    private RecordingEmailService mail;

    /** A pen stroke on white paper, like a photographed signature. */
    private static byte[] signaturePng() throws Exception {
        BufferedImage img = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 200);
        g.setColor(new Color(20, 30, 90));
        g.setStroke(new BasicStroke(6));
        g.drawLine(120, 140, 260, 60);
        g.drawLine(260, 60, 330, 130);
        g.drawLine(330, 130, 480, 70);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** A round red stamp. */
    private static byte[] sealPng() throws Exception {
        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 300);
        g.setColor(new Color(170, 20, 30));
        g.setStroke(new BasicStroke(10));
        g.drawOval(30, 30, 240, 240);
        g.drawOval(70, 70, 160, 160);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(d);
        }
    }

    private String templateOf(Session s, String kind) throws Exception {
        for (JsonNode t : getJson("/api/v1/documents/templates", s)) {
            if (kind.equals(t.get("kind").asText())) return t.get("id").asText();
        }
        throw new AssertionError("no " + kind + " template");
    }

    private String employeeNamed(Session s, String first) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", s)) {
            if (e.toString().contains(first)) return e.get("id").asText();
        }
        throw new AssertionError("no employee " + first);
    }

    private JsonNode issue(Session s, Map<String, Object> req) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/documents")
                        .header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(String url, Session s, Object body) throws Exception {
        var b = post(url).contentType(MediaType.APPLICATION_JSON).content(json(body));
        if (s != null) b = b.header("Authorization", "Bearer " + s.accessToken());
        return objectMapper.readTree(mockMvc.perform(b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String drawn() throws Exception {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(signaturePng());
    }

    @Test
    @DisplayName("the default signatory's signature and the seal print above the signatory's name")
    void signatory_signature_and_seal_print() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");

        JsonNode s = postJson("/api/v1/documents/signatories", owner, Map.of("name", "Ava Chen", "title", "Head of People"));
        assertThat(s.get("isDefault").asBoolean()).as("the first signatory is the default").isTrue();
        String sid = s.get("id").asText();
        mockMvc.perform(multipart("/api/v1/documents/signatories/" + sid + "/image")
                        .file(new MockMultipartFile("file", "sig.png", "image/png", signaturePng()))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasImage").value(true));
        mockMvc.perform(multipart("/api/v1/documents/letterhead/seal")
                        .file(new MockMultipartFile("file", "seal.png", "image/png", sealPng()))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasSeal").value(true));

        // The stored signature is cleaned: transparent paper, cropped to the ink.
        byte[] stored = mockMvc.perform(get("/api/v1/documents/signatories/" + sid + "/image")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        BufferedImage cleaned = ImageIO.read(new java.io.ByteArrayInputStream(stored));
        assertThat(cleaned.getColorModel().hasAlpha()).isTrue();
        assertThat(cleaned.getRGB(0, 0) >>> 24).as("the paper is transparent").isZero();
        assertThat(cleaned.getWidth()).isLessThan(600);

        JsonNode doc = issue(owner, Map.of("templateId", templateOf(owner, "JOINING_LETTER"),
                "employeeId", employeeNamed(owner, "Priya")));
        assertThat(doc.get("signatoryId").asText()).isEqualTo(sid);
        String body = doc.get("body").asText();
        assertThat(body).contains(SignatoryService.SIGNATURE_LINE + "\nAva Chen");

        byte[] pdf = mockMvc.perform(get("/api/v1/documents/" + doc.get("id").asText() + "/pdf")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(text(pdf)).contains("Ava Chen").doesNotContain("[[signature]]");
        try (PDDocument d = Loader.loadPDF(pdf)) {
            int images = 0;
            for (var page : d.getPages()) {
                for (var name : page.getResources().getXObjectNames()) {
                    if (page.getResources().isImageXObject(name)) images++;
                }
            }
            assertThat(images).as("signature and seal drawn").isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("an employee reads, signs and gets an audited signed copy; nobody else can open it")
    void employee_signs_in_orbit() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");
        Session priya = login("priya.nair@northwind.demo", "demopass123");
        Session marcus = login("marcus.reed@northwind.demo", "demopass123");

        String docId = issue(owner, Map.of("templateId", templateOf(owner, "JOINING_LETTER"),
                "employeeId", employeeNamed(owner, "Priya"))).get("id").asText();
        int before = mail.signatureRequests().size();
        JsonNode sent = postJson("/api/v1/documents/" + docId + "/signature", owner, Map.of("message", "Welcome aboard!"));
        assertThat(sent.get("status").asText()).isEqualTo("SENT");
        assertThat(mail.signatureRequests()).hasSize(before + 1);
        assertThat(mail.signatureRequests().get(before).to()).isEqualTo("priya.nair@northwind.demo");
        String rid = sent.get("id").asText();
        assertThat(mail.signatureRequests().get(before).url()).endsWith("/me/letters/" + rid);

        JsonNode mine = getJson("/api/v1/me/letters", priya);
        assertThat(mine.toString()).contains(rid);
        mockMvc.perform(get("/api/v1/me/letters/" + rid).header("Authorization", "Bearer " + marcus.accessToken()))
                .andExpect(status().isNotFound());

        JsonNode view = getJson("/api/v1/me/letters/" + rid, priya);
        assertThat(view.get("status").asText()).isEqualTo("VIEWED");
        assertThat(view.get("pages").size()).isGreaterThan(0);
        assertThat(view.get("pages").get(0).asText()).startsWith("data:image/png;base64,");

        // Unticked, or no name: refused.
        mockMvc.perform(post("/api/v1/me/letters/" + rid + "/accept").header("Authorization", "Bearer " + priya.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("typedName", "Priya Nair", "signature", drawn(), "agree", false))))
                .andExpect(status().isBadRequest());

        JsonNode signed = postJson("/api/v1/me/letters/" + rid + "/accept", priya,
                Map.of("typedName", "Priya Nair", "signature", drawn(), "agree", true));
        assertThat(signed.get("status").asText()).isEqualTo("SIGNED");
        assertThat(getJson("/api/v1/notifications", owner).toString()).contains("LETTER_SIGNED");

        byte[] pdf = mockMvc.perform(get("/api/v1/documents/" + docId + "/pdf")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String text = text(pdf);
        assertThat(text).contains("Accepted", "Priya Nair", "Signature record", "SHA-256");

        // Signed twice: no. Deleted: no — it is a record now.
        mockMvc.perform(post("/api/v1/me/letters/" + rid + "/accept").header("Authorization", "Bearer " + priya.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("typedName", "Priya Nair", "signature", drawn(), "agree", true))))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/documents/" + docId).header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a candidate signs through a single-letter link without logging in; a bad link is refused")
    void candidate_signs_by_link() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");
        Map<String, Object> req = new HashMap<>();
        req.put("templateId", templateOf(owner, "OFFER_LETTER"));
        req.put("overrides", Map.of("employee.fullName", "Dana Scully", "employee.firstName", "Dana"));
        String docId = issue(owner, req).get("id").asText();

        // No employee: an email address is needed.
        mockMvc.perform(post("/api/v1/documents/" + docId + "/signature").header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of())))
                .andExpect(status().isBadRequest());
        int before = mail.signatureRequests().size();
        postJson("/api/v1/documents/" + docId + "/signature", owner, Map.of("email", "dana@example.com"));
        String link = mail.signatureRequests().get(before).url();
        assertThat(link).contains("/sign/");
        String token = link.substring(link.lastIndexOf('/') + 1);

        mockMvc.perform(get("/api/v1/public/sign/not-a-real-token-at-all-0000")).andExpect(status().isNotFound());
        JsonNode view = objectMapper.readTree(mockMvc.perform(get("/api/v1/public/sign/" + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(view.get("companyName").asText()).isEqualTo("Northwind Robotics");
        assertThat(view.get("pages").size()).isGreaterThan(0);

        JsonNode signed = postJson("/api/v1/public/sign/" + token + "/accept", null,
                Map.of("typedName", "Dana Scully", "signature", drawn(), "agree", true));
        assertThat(signed.get("status").asText()).isEqualTo("SIGNED");
        byte[] copy = mockMvc.perform(get("/api/v1/public/sign/" + token + "/pdf"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(text(copy)).contains("Dana Scully", "Signature record");

        JsonNode history = getJson("/api/v1/documents/" + docId + "/signature", owner);
        assertThat(history.get(0).get("status").asText()).isEqualTo("SIGNED");
        assertThat(history.get(0).get("typedName").asText()).isEqualTo("Dana Scully");
    }

    @Test
    @DisplayName("declining needs a reason; withdrawing stops the link")
    void decline_and_withdraw() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");
        Map<String, Object> req = new HashMap<>();
        req.put("templateId", templateOf(owner, "OFFER_LETTER"));
        req.put("overrides", Map.of("employee.fullName", "Fox Mulder"));
        String docId = issue(owner, req).get("id").asText();

        int before = mail.signatureRequests().size();
        JsonNode first = postJson("/api/v1/documents/" + docId + "/signature", owner, Map.of("email", "fox@example.com"));
        String token = mail.signatureRequests().get(before).url().replaceAll(".*/", "");
        postJson("/api/v1/documents/signature-requests/" + first.get("id").asText() + "/withdraw", owner, Map.of());
        mockMvc.perform(get("/api/v1/public/sign/" + token)).andExpect(status().isNotFound());

        postJson("/api/v1/documents/" + docId + "/signature", owner, Map.of("email", "fox@example.com"));
        String token2 = mail.signatureRequests().get(mail.signatureRequests().size() - 1).url().replaceAll(".*/", "");
        mockMvc.perform(post("/api/v1/public/sign/" + token2 + "/decline").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", ""))))
                .andExpect(status().isBadRequest());
        JsonNode declined = postJson("/api/v1/public/sign/" + token2 + "/decline", null,
                Map.of("reason", "The joining date does not work for me."));
        assertThat(declined.get("status").asText()).isEqualTo("DECLINED");
        assertThat(getJson("/api/v1/notifications", owner).toString()).contains("LETTER_DECLINED");
    }
}
