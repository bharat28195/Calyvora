package com.calyvora.document;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Letters of any length on the company letterpad (PD-69): the writing area is measured from the
 * letterpad itself, page two onwards uses the continuation sheet, and no line of the letter lands on
 * the artwork.
 */
class LetterpadPagesTest extends IntegrationTestBase {

    private static final String PW = "Passw0rd!x";
    private static final float MM = 72f / 25.4f;
    private static final float H = PDRectangle.A4.getHeight();

    /**
     * A letterpad shaped like the ones printers deliver: page one with a logo and address block over a
     * rule at 48 mm, a pale triangle from 225 mm on the right, a tagline and a dark band at the foot;
     * page two the same without the header.
     */
    private static byte[] letterpadPdf(boolean withSecondPage) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int p = 0; p < (withSecondPage ? 2 : 1); p++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    if (p == 0) {
                        text(cs, "Northwind Robotics", 20, 30, 22);
                        text(cs, "123 Innovation Way, Austin", 140, 20, 9);
                        cs.setNonStrokingColor(new Color(0x1f, 0x6f, 0xd8));
                        cs.addRect(20 * MM, H - 48 * MM, 170 * MM, 0.8f * MM);
                        cs.fill();
                    }
                    cs.setNonStrokingColor(new Color(0xd6, 0xe4, 0xf5));
                    cs.moveTo(130 * MM, H - 280 * MM);
                    cs.lineTo(200 * MM, H - 225 * MM);
                    cs.lineTo(210 * MM, H - 280 * MM);
                    cs.closePath();
                    cs.fill();
                    text(cs, "Robotics for a Smarter, Safer World", 20, 268, 10);
                    cs.setNonStrokingColor(new Color(0x15, 0x30, 0x5a));
                    cs.addRect(0, 0, 210 * MM, 7 * MM);
                    cs.fill();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void text(PDPageContentStream cs, String s, float xMm, float yMm, float size) throws Exception {
        cs.setNonStrokingColor(new Color(0x15, 0x30, 0x5a));
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), size);
        cs.newLineAtOffset(xMm * MM, H - yMm * MM);
        cs.showText(s);
        cs.endText();
    }

    private JsonNode upload(Session s, String path, String name, String type, byte[] bytes) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/documents/letterhead/" + path)
                        .file(new MockMultipartFile("file", name, type, bytes))
                        .header("Authorization", "Bearer " + s.accessToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private byte[] image(Session s, String path) throws Exception {
        return mockMvc.perform(get("/api/v1/documents/letterhead/" + path)
                        .header("Authorization", "Bearer " + s.accessToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    @DisplayName("a two-page letterpad: the writing area is measured, page two becomes the continuation sheet")
    void two_page_letterpad_is_measured() throws Exception {
        Session owner = onboardOwner("Pagesco", "admin@pagesco.test", PW);
        JsonNode lh = upload(owner, "background", "letterpad.pdf", "application/pdf", letterpadPdf(true));

        assertThat(lh.get("continuationSource").asText()).isEqualTo("PAGE2");
        // Below the rule at 48 mm, with air; above the triangle at 225 mm, with air.
        assertThat(lh.get("firstTopMm").asInt()).isBetween(52, 62);
        assertThat(lh.get("firstBottomMm").asInt()).isBetween(74, 84);
        // Page two has no header: writing starts near the top.
        assertThat(lh.get("laterTopMm").asInt()).isBetween(16, 24);
        assertThat(lh.get("laterBottomMm").asInt()).isBetween(74, 84);
        // The left edge of the letterpad's own printing, at 20 mm.
        assertThat(lh.get("sideMm").asInt()).isBetween(18, 22);
        assertThat(image(owner, "continuation")).isNotEmpty();
    }

    @Test
    @DisplayName("a one-page letterpad gets a continuation sheet made from it, with the header cleared")
    void one_page_letterpad_derives_its_continuation() throws Exception {
        Session owner = onboardOwner("Pagesco2", "admin@pagesco2.test", PW);
        JsonNode lh = upload(owner, "background", "letterpad.pdf", "application/pdf", letterpadPdf(false));
        assertThat(lh.get("continuationSource").asText()).isEqualTo("DERIVED");
        assertThat(lh.get("laterTopMm").asInt()).isBetween(16, 24);

        BufferedImage later = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image(owner, "continuation")));
        // Where the logo was on page one, the continuation sheet is blank paper.
        int y = (int) (later.getHeight() * 28.0 / 297), x = (int) (later.getWidth() * 25.0 / 210);
        assertThat(later.getRGB(x, y) & 0xffffff).isEqualTo(0xffffff);
        // The footer band is still there.
        assertThat(later.getRGB(later.getWidth() / 2, later.getHeight() - 5) & 0xffffff).isNotEqualTo(0xffffff);

        // A separate continuation sheet replaces it; removing that goes back to the derived one.
        assertThat(upload(owner, "continuation", "page2.pdf", "application/pdf", letterpadPdf(false))
                .get("continuationSource").asText()).isEqualTo("UPLOADED");
        String back = mockMvc.perform(delete("/api/v1/documents/letterhead/continuation")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(back).get("continuationSource").asText()).isEqualTo("DERIVED");
    }

    @Test
    @DisplayName("a long letter flows over several pages and never writes on the artwork")
    void long_letter_stays_inside_the_writing_area() throws Exception {
        Session owner = onboardOwner("Pagesco3", "admin@pagesco3.test", PW);
        JsonNode lh = upload(owner, "background", "letterpad.pdf", "application/pdf", letterpadPdf(true));
        LetterPdf.Area area = new LetterPdf.Area(lh.get("firstTopMm").asInt(), lh.get("firstBottomMm").asInt(),
                lh.get("laterTopMm").asInt(), lh.get("laterBottomMm").asInt(), lh.get("sideMm").asInt());

        StringBuilder body = new StringBuilder("Dear Priya,\n\n");
        for (int i = 1; i <= 60; i++) {
            body.append("Paragraph ").append(i).append(": the terms of your appointment are set out here in full ")
                    .append("so that this letter runs over several pages of the letterpad.\n\n");
        }
        byte[] pdf = LetterPdf.render(body.toString(), new LetterPdf.Stationery("Pagesco3", null, null, null, true,
                null, image(owner, "background"), image(owner, "continuation"), area));

        List<List<Float>> tops = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(3);
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                List<Float> ys = new ArrayList<>();
                PDFTextStripper stripper = new PDFTextStripper() {
                    @Override
                    protected void writeString(String text, List<TextPosition> positions) {
                        for (TextPosition t : positions) ys.add(t.getYDirAdj() / MM);
                    }
                };
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                stripper.getText(doc);
                tops.add(ys);
            }
        }
        for (int p = 0; p < tops.size(); p++) {
            float top = p == 0 ? area.firstTop() : area.laterTop();
            float bottom = 297 - (p == 0 ? area.firstBottom() : area.laterBottom());
            assertThat(tops.get(p)).as("page %d", p + 1).isNotEmpty()
                    .allSatisfy(y -> assertThat(y).isBetween(top - 1, bottom + 1));
        }
    }
}
