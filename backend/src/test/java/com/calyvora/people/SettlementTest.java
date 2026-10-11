package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full and final settlement (PD-73): the rules on their own, then a settlement end to end. */
class SettlementTest extends IntegrationTestBase {

    @Nested
    class Rules {

        @Test
        @DisplayName("gratuity: due at five years, or four years and 240 days; part-years of six months count")
        void gratuity_eligibility() {
            LocalDate start = LocalDate.of(2020, 1, 1);
            assertThat(SettlementMath.service(start, LocalDate.of(2024, 6, 30)).gratuityDue()).as("4y 6m: 182 days").isFalse();
            assertThat(SettlementMath.service(start, LocalDate.of(2024, 8, 28)).gratuityDue()).as("4y + 240 days").isTrue();
            var five7 = SettlementMath.service(start, LocalDate.of(2025, 7, 31));
            assertThat(five7.gratuityDue()).isTrue();
            assertThat(five7.yearsCounted()).as("5 years 7 months counts as 6").isEqualTo(6);
            // 15/26 × 52,000 × 6 = 1,80,000
            assertThat(SettlementMath.gratuity(five7, new BigDecimal("52000"))).isEqualByComparingTo("180000.00");
            // Capped at ₹20 lakh.
            var long30 = SettlementMath.service(LocalDate.of(1990, 1, 1), LocalDate.of(2025, 12, 31));
            assertThat(SettlementMath.gratuity(long30, new BigDecimal("500000"))).isEqualByComparingTo("2000000");
        }

        @Test
        @DisplayName("notice periods are read from how people write them")
        void notice_parsing() {
            assertThat(SettlementMath.noticeDays("60 days")).isEqualTo(60);
            assertThat(SettlementMath.noticeDays("Two months' notice")).isEqualTo(60);
            assertThat(SettlementMath.noticeDays("1 month")).isEqualTo(30);
            assertThat(SettlementMath.noticeDays("2 weeks")).isEqualTo(14);
            assertThat(SettlementMath.noticeDays("as agreed")).isNull();
        }

        @Test
        @DisplayName("a day's pay: basic ÷ 26 by default; the pay-by date skips the weekend")
        void day_rate_and_pay_by() {
            assertThat(SettlementMath.perDay("BASIC_26", new BigDecimal("52000"), new BigDecimal("100000"))).isEqualByComparingTo("2000.00");
            assertThat(SettlementMath.perDay("GROSS_30", new BigDecimal("52000"), new BigDecimal("90000"))).isEqualByComparingTo("3000.00");
            // Friday 10 Oct 2025 → Tuesday 14 Oct.
            assertThat(SettlementService.payBy(LocalDate.of(2025, 10, 10))).isEqualTo(LocalDate.of(2025, 10, 14));
        }
    }

    private String priya(Session admin) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", admin)) {
            if (e.toString().contains("Priya")) return e.get("id").asText();
        }
        throw new AssertionError("no Priya");
    }

    private JsonNode send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, Session s,
                          Object body, int expected) throws Exception {
        b = b.header("Authorization", "Bearer " + s.accessToken());
        if (body != null) b = b.contentType(MediaType.APPLICATION_JSON).content(json(body));
        String out = mockMvc.perform(b).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return out.isEmpty() ? null : objectMapper.readTree(out);
    }

    @Test
    @DisplayName("an exit's settlement: final salary from payroll, encashment, notice recovery, HR lines, then approve, letter, paid")
    void settlement_end_to_end() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session admin = login("ava.chen@northwind.demo", "demopass123");
        Session priyaS = login("priya.nair@northwind.demo", "demopass123");
        String priya = priya(admin);
        String base = "/api/v1/people/employees/" + priya + "/settlement";

        // Not leaving yet: nothing to settle.
        send(get(base), admin, null, 409);

        send(patch("/api/v1/documents/letterhead"), admin, Map.of("noticePeriod", "60 days"), 200);
        LocalDate last = LocalDate.now().plusDays(10);
        send(post("/api/v1/people/employees/" + priya + "/exit"), admin,
                Map.of("lastWorkingDay", last.toString(), "reason", "Moving cities"), 200);

        // Salary data: payroll only.
        send(get(base), priyaS, null, 403);

        JsonNode v = send(get(base), admin, null, 200);
        assertThat(v.get("status").asText()).isEqualTo("DRAFT");
        JsonNode st = v.get("statement");
        String earnings = st.get("earnings").toString();
        assertThat(earnings).contains("Salary for");
        assertThat(st.get("noticeRequired").asInt()).isEqualTo(60);
        assertThat(st.get("noticeServed").asInt()).isEqualTo(11);
        assertThat(st.get("deductions").toString()).contains("Notice period shortfall");
        BigDecimal net = new BigDecimal(st.get("netPayable").asText());
        BigDecimal te = new BigDecimal(st.get("totalEarnings").asText());
        BigDecimal td = new BigDecimal(st.get("totalDeductions").asText());
        assertThat(net).isEqualByComparingTo(te.subtract(td));

        // HR waives the notice, encashes 10 days and adds a bonus and an asset recovery.
        JsonNode u = send(patch(base), admin, Map.of("noticeWaived", true, "encashDays", 10,
                "extraLines", List.of(Map.of("label", "Joining bonus clawback waived — retention bonus", "amount", 5000, "deduction", false),
                        Map.of("label", "Laptop not returned", "amount", 30000, "deduction", true))), 200);
        JsonNode st2 = u.get("statement");
        assertThat(st2.get("deductions").toString()).doesNotContain("Notice period shortfall").contains("Laptop not returned");
        assertThat(st2.get("earnings").toString()).contains("Leave encashment", "10 days", "retention bonus");
        assertThat(st2.get("notes").toString()).contains("waived");

        JsonNode a = send(post(base + "/approve"), admin, null, 200);
        assertThat(a.get("status").asText()).isEqualTo("APPROVED");
        assertThat(a.get("documentId").isNull()).as("the F&F letter was issued").isFalse();
        JsonNode letter = getJson("/api/v1/documents/" + a.get("documentId").asText(), admin);
        assertThat(letter.get("kind").asText()).isEqualTo("FNF_STATEMENT");
        assertThat(letter.get("body").asText()).contains("Full and final settlement", "Net amount payable");

        // Frozen: no edits without reopening.
        send(patch(base), admin, Map.of("noticeWaived", false), 409);
        JsonNode paid = send(post(base + "/paid"), admin, Map.of("paidOn", last.plusDays(2).toString(), "reference", "NEFT 12345"), 200);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("paidReference").asText()).isEqualTo("NEFT 12345");
        send(post(base + "/reopen"), admin, null, 409);
    }
}
