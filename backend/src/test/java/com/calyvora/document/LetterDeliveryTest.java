package com.calyvora.document;

import com.calyvora.support.IntegrationTestBase;
import com.calyvora.support.RecordingEmailService;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Letters leaving Orbit (PD-64): rendered to PDF with the right characters and figures, letterpads
 * accepted as PDF and Word, and a letter emailed to the employee with the send recorded on it.
 */
class LetterDeliveryTest extends IntegrationTestBase {

    @Autowired
    private RecordingEmailService mail;

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(d);
        }
    }

    @Test
    @DisplayName("a letter renders to PDF with its table, the rupee sign and dashes intact, page breaks honoured")
    void renders() throws Exception {
        String body = """
                01 Aug, 2026

                # Salary increment letter

                Dear **Dana**,

                Your revised salary is ₹27,68,832.00 — effective *1 August*.

                - first point
                1. numbered point

                | EARNINGS (PART A) | MONTHLY (INR) | YEARLY (INR) |
                |---|---|---|
                | Basic Salary | 1,15,368.00 | 13,84,416.00 |
                | **TOTAL** | **2,30,736.00** | **27,68,832.00** |

                --- page ---

                # Annexure I
                """;
        byte[] pdf = LetterPdf.render(body, new LetterPdf.Stationery("Northwind Robotics", "1 MG Road\nPune",
                "CIN: U123  ·  GSTIN: 27ABC", "#7c5cff", true, null, null));
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text).contains("₹27,68,832.00", "—", "13,84,416.00", "27,68,832.00", "Northwind Robotics", "CIN: U123");
        try (PDDocument d = Loader.loadPDF(pdf)) {
            assertThat(d.getNumberOfPages()).as("the annexure starts a page").isEqualTo(2);
        }
    }

    @Test
    @DisplayName("a PDF letterpad becomes the page image, and letters are drawn on it")
    void pdf_letterpad() throws Exception {
        Session owner = onboardOwner("Padpdf", "admin@padpdf.test", "Passw0rd!x");
        mockMvc.perform(multipart("/api/v1/documents/letterhead/background")
                        .file(new MockMultipartFile("file", "stationery.pdf", "application/pdf", aPdf()))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasBackground").value(true))
                .andExpect(jsonPath("$.useBackground").value(true));
        byte[] image = mockMvc.perform(get("/api/v1/documents/letterhead/background")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        var img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image));
        // A4 at 150 dpi: 1240 × 1754.
        assertThat(img.getWidth()).isEqualTo(1240);
        assertThat(img.getHeight()).isBetween(1753, 1754);   // rounding of 841.89 pt
    }

    @Test
    @DisplayName("a Word letterpad is accepted too")
    void docx_letterpad() throws Exception {
        Session owner = onboardOwner("Paddocx", "admin@paddocx.test", "Passw0rd!x");
        ByteArrayOutputStream docx = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Northwind Robotics · CIN U123");
            doc.write(docx);
        }
        mockMvc.perform(multipart("/api/v1/documents/letterhead/background")
                        .file(new MockMultipartFile("file", "stationery.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx.toByteArray()))
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasBackground").value(true));
    }

    @Test
    @DisplayName("an issued letter downloads as PDF and emails to the employee, the send recorded")
    void download_and_email() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");
        String templateId = null;
        for (JsonNode t : getJson("/api/v1/documents/templates", owner)) {
            if ("JOINING_LETTER".equals(t.get("kind").asText())) templateId = t.get("id").asText();
        }
        String employeeId = getJson("/api/v1/people/employees", owner).get(1).get("id").asText();
        String docId = objectMapper.readTree(mockMvc.perform(post("/api/v1/documents")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", templateId, "employeeId", employeeId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        byte[] pdf = mockMvc.perform(get("/api/v1/documents/" + docId + "/pdf")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(text(pdf)).contains("Northwind Robotics");

        JsonNode defaults = getJson("/api/v1/documents/" + docId + "/email", owner);
        String to = defaults.get("to").asText();
        assertThat(to).contains("@");
        assertThat(defaults.get("sent").size()).isZero();

        int before = mail.documents().size();
        JsonNode after = objectMapper.readTree(mockMvc.perform(post("/api/v1/documents/" + docId + "/email")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("to", to, "subject", defaults.get("subject").asText(),
                                "message", defaults.get("message").asText()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(mail.documents()).hasSize(before + 1);
        assertThat(mail.documents().get(before).url()).endsWith(".pdf");
        assertThat(after.get("sent").size()).isEqualTo(1);
        assertThat(after.get("sent").get(0).get("delivered").asBoolean()).isTrue();

        // A bad address is refused before anything is sent.
        mockMvc.perform(post("/api/v1/documents/" + docId + "/email")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("to", "not-an-email"))))
                .andExpect(status().isBadRequest());
        assertThat(mail.documents()).hasSize(before + 1);
    }

    /** A one-page A4 PDF with a band across the top, like a letterpad. */
    private static byte[] aPdf() throws Exception {
        try (PDDocument d = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            d.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(d, page)) {
                cs.setNonStrokingColor(0.48f, 0.36f, 1f);
                cs.addRect(0, PDRectangle.A4.getHeight() - 60, PDRectangle.A4.getWidth(), 60);
                cs.fill();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            d.save(out);
            return out.toByteArray();
        }
    }
}
