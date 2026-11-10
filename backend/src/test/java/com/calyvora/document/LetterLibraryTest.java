package com.calyvora.document;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The letter library (PD-63): every kind of letter seeded, amounts the way Indian letters print them,
 * the salary annexure adding up, and existing companies given the new letters without losing — or
 * getting back — anything of their own.
 */
class LetterLibraryTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Nested
    @DisplayName("amounts")
    class Amounts {

        @Test
        @DisplayName("lakh and crore grouping, always two decimals")
        void indian_grouping() {
            assertThat(MergeFields.inr(new BigDecimal("2768832"))).isEqualTo("27,68,832.00");
            assertThat(MergeFields.inr(new BigDecimal("230736"))).isEqualTo("2,30,736.00");
            assertThat(MergeFields.inr(new BigDecimal("1800"))).isEqualTo("1,800.00");
            assertThat(MergeFields.inr(new BigDecimal("999"))).isEqualTo("999.00");
            assertThat(MergeFields.inr(new BigDecimal("123456789.5"))).isEqualTo("12,34,56,789.50");
        }

        @Test
        @DisplayName("in words, as the two reference letters print them")
        void in_words() {
            assertThat(MergeFields.inWords(new BigDecimal("2768832")))
                    .isEqualTo("Twenty Seven Lakh Sixty Eight Thousand Eight Hundred and Thirty Two");
            assertThat(MergeFields.inWords(new BigDecimal("2517120")))
                    .isEqualTo("Twenty Five Lakh Seventeen Thousand One Hundred and Twenty");
            assertThat(MergeFields.inWords(new BigDecimal("125000000")))
                    .isEqualTo("Twelve Crore Fifty Lakh");
            assertThat(MergeFields.inWords(new BigDecimal("1015.75"))).isEqualTo("One Thousand Fifteen");
        }

        @Test
        @DisplayName("the three date styles")
        void date_styles() {
            java.time.LocalDate d = java.time.LocalDate.of(2026, 8, 1);
            assertThat(MergeFields.date(d, "LONG")).isEqualTo("1 August 2026");
            assertThat(MergeFields.date(d, "SHORT")).isEqualTo("01 Aug, 2026");
            assertThat(MergeFields.date(d, "NUMERIC")).isEqualTo("01/08/2026");
        }

        @Test
        @DisplayName("a typed salary reads the same however it was typed")
        void typed_salary() {
            assertThat(DocumentService.parseAmount("27,68,832")).isEqualByComparingTo("2768832");
            assertThat(DocumentService.parseAmount("₹ 27,68,832.00")).isEqualByComparingTo("2768832");
            assertThat(DocumentService.parseAmount("abc")).isNull();
        }
    }

    @Test
    @DisplayName("every kind of letter has a starter, and no starter says his or her")
    void the_library_is_complete_and_neutral() {
        Set<DocumentKind> kinds = new HashSet<>();
        for (StarterTemplates.Starter s : StarterTemplates.all()) {
            kinds.add(s.kind());
            assertThat(s.body()).as(s.name()).doesNotContainPattern("\\b(his|her|him|he|she)\\b");
        }
        for (DocumentKind k : DocumentKind.values()) {
            if (k != DocumentKind.CUSTOM) assertThat(kinds).as("a starter for " + k).contains(k);
        }
    }

    @Test
    @DisplayName("an appointment letter carries the company's terms and a salary annexure that adds up")
    void appointment_letter() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");
        mockMvc.perform(patch("/api/v1/documents/letterhead").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dateStyle", "SHORT", "probationDays", 90, "noticePeriod", "three (3) months",
                                "noticeProbation", "ten (10) working days", "workingDays", "Monday to Friday",
                                "workingHours", "9:30 am to 6:30 pm", "payDay", "on or before the 7th of the following month",
                                "jurisdiction", "Ahmedabad, Gujarat"))))
                .andExpect(status().isOk());

        String templateId = templateOfKind(owner, "APPOINTMENT_LETTER");
        String employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();
        JsonNode preview = postJson("/api/v1/documents/preview", owner,
                Map.of("templateId", templateId, "employeeId", employeeId));
        String body = preview.get("body").asText();

        assertThat(body).contains("three (3) months", "ten (10) working days", "Ahmedabad, Gujarat", "90 days")
                .contains("| EARNINGS (PART A) |").contains("TOTAL SALARY (A)")
                .doesNotContain("{{");
        assertThat(body).containsPattern("\\d{2} [A-Z][a-z]{2}, \\d{4}");   // the SHORT date style

        // The yearly column adds up to the annual figure printed in the letter, to the paisa.
        JsonNode values = preview.get("values");
        String annual = values.get("salary.annual").asText();
        assertThat(annual).matches("[0-9,]+\\.\\d{2}");
        BigDecimal yearlyTotal = BigDecimal.ZERO;
        for (String line : values.get("salary.structure").asText().split("\n")) {
            String[] cells = line.split("\\|");
            if (cells.length < 4 || line.contains("---") || line.contains("PART") || line.contains("TOTAL")
                    || line.contains("SUB-TOTAL") || line.contains("COST TO")) continue;
            if (line.contains("PF") || line.contains("ESI")) continue;   // employer contributions are Part B
            yearlyTotal = yearlyTotal.add(new BigDecimal(cells[3].replace("*", "").replace(",", "").trim()));
        }
        assertThat(yearlyTotal).isEqualByComparingTo(new BigDecimal(annual.replace(",", "")));
        assertThat(values.get("salary.annualInWords").asText()).endsWith("Rupees");
    }

    @Test
    @DisplayName("an offer for somebody not yet employed builds the annexure from the salary typed in")
    void offer_from_typed_salary() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");
        String templateId = templateOfKind(owner, "APPOINTMENT_LETTER");
        Map<String, Object> req = new HashMap<>();
        req.put("templateId", templateId);
        req.put("overrides", Map.of("employee.fullName", "Dana Scully", "salary.annual", "27,68,832"));
        JsonNode values = postJson("/api/v1/documents/preview", owner, req).get("values");
        assertThat(values.get("salary.annual").asText()).isEqualTo("27,68,832.00");
        assertThat(values.get("salary.annualInWords").asText())
                .isEqualTo("Twenty Seven Lakh Sixty Eight Thousand Eight Hundred and Thirty Two Rupees");
        assertThat(values.get("salary.structure").asText()).contains("27,68,832.00");
    }

    @Test
    @DisplayName("a company that had the first five gets the rest once, and a deleted letter stays deleted")
    void existing_companies_get_the_new_letters_once() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");
        int all = StarterTemplates.all().size();
        assertThat(getJson("/api/v1/documents/templates", owner).size()).isEqualTo(all);

        // Make it look like a company from before the library: only set 1, and the marker at 1.
        String companyId = jdbc.queryForObject("select company_id::text from users where email = 'owner@acme.com'", String.class);
        asTenant(companyId, c -> {
            try (var st = c.prepareStatement("delete from document_templates where company_id = ?::uuid and kind not in "
                    + "('OFFER_LETTER','JOINING_LETTER','RELIEVING_LETTER','EXPERIENCE_LETTER','PROMOTION_LETTER')")) {
                st.setString(1, companyId);
                assertThat(st.executeUpdate()).isEqualTo(all - 5);
            }
            try (var st = c.prepareStatement("update company_settings set letter_starters_seeded = 1 where company_id = ?::uuid")) {
                st.setString(1, companyId);
                assertThat(st.executeUpdate()).isEqualTo(1);
            }
            return null;
        });
        // Opening Documents tops the library up to the full set, once.
        assertThat(getJson("/api/v1/documents/templates", owner).size()).isEqualTo(all);
        assertThat(getJson("/api/v1/documents/templates", owner).size()).isEqualTo(all);

        // Delete one; it is not put back.
        String warning = templateOfKind(owner, "WARNING_LETTER");
        mockMvc.perform(delete("/api/v1/documents/templates/" + warning).header("Authorization", bearer(owner)))
                .andExpect(status().is2xxSuccessful());
        assertThat(getJson("/api/v1/documents/templates", owner).size()).isEqualTo(all - 1);
        assertThat(getJson("/api/v1/documents/templates", owner).size()).isEqualTo(all - 1);
    }

    /** One connection, tenant bound for its duration and cleared after (the V59 test's pattern). */
    private void asTenant(String companyId, org.springframework.jdbc.core.ConnectionCallback<Object> work) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Object>) c -> {
            try (var st = c.prepareStatement("select set_config('calyvora.company_id', ?, false)")) {
                st.setString(1, companyId);
                st.execute();
            }
            try {
                return work.doInConnection(c);
            } finally {
                try (var st = c.prepareStatement("select set_config('calyvora.company_id', '', false)")) {
                    st.execute();
                }
            }
        });
    }

    private static String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private JsonNode postJson(String path, Session s, Object body) throws Exception {
        var res = mockMvc.perform(post(path).header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is2xxSuccessful()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private String templateOfKind(Session owner, String kind) throws Exception {
        for (JsonNode t : getJson("/api/v1/documents/templates", owner)) {
            if (kind.equals(t.get("kind").asText())) return t.get("id").asText();
        }
        throw new AssertionError("no starter template of kind " + kind);
    }
}
