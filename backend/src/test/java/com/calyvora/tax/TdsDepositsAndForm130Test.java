package com.calyvora.tax;

import com.calyvora.platform.PlatformOwnerBootstrap;
import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The challan register and what it does to Form 130 Part A: a finalised month's TDS shows as deducted,
 * and as deposited only once HR records the challan it was paid on.
 */
class TdsDepositsAndForm130Test extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Autowired
    private PlatformOwnerBootstrap platformOwnerBootstrap;

    private Session setUp(String month) throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", PW);
        platformOwnerBootstrap.ensurePlatformOwner();
        Session platform = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        String companyId = null;
        for (JsonNode c : getJson("/api/v1/platform/companies", platform)) {
            if ("Northwind Robotics".equals(c.get("name").asText())) companyId = c.get("companyId").asText();
        }
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/features")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("feature", "INCOME_TAX", "enabled", true))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payroll/months/" + month + "/finalize")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        return owner;
    }

    private static JsonNode monthRow(JsonNode deposits, String month) {
        for (JsonNode q : deposits.get("quarters")) {
            for (JsonNode m : q.get("months")) {
                if (month.equals(m.get("month").asText())) return m;
            }
        }
        throw new AssertionError("no row for " + month);
    }

    private org.springframework.test.web.servlet.ResultActions putJson(String path, Session s, Object body) throws Exception {
        return mockMvc.perform(put(path).header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    @DisplayName("a recorded challan turns deducted into deposited on Part A, month and quarter")
    void challan_reaches_part_a() throws Exception {
        YearMonth month = YearMonth.now().minusMonths(1);
        String fy = FinancialYear.of(month.atDay(1)).label();
        int quarterIndex = (int) (YearMonth.from(FinancialYear.of(month.atDay(1)).start()).until(month, java.time.temporal.ChronoUnit.MONTHS) / 3);
        String quarter = fy + "-Q" + (quarterIndex + 1);
        Session owner = setUp(month.toString());

        JsonNode before = getJson("/api/v1/tax/deposits?year=" + fy, owner);
        JsonNode row = monthRow(before, month.toString());
        assertThat(row.get("finalised").asBoolean()).isTrue();
        long deducted = row.get("tdsDeducted").asLong();
        assertThat(deducted).as("Northwind's salaries are taxable").isPositive();
        assertThat(row.get("bsrCode").isNull()).isTrue();

        // Part A before any challan: deducted, nothing deposited, and the month listed as unpaid.
        JsonNode formBefore = getJson("/api/v1/tax/me/form130?year=" + fy, owner);
        JsonNode qBefore = formBefore.get("quarters").get(quarterIndex);
        assertThat(qBefore.get("tds").asLong()).isPositive();
        assertThat(qBefore.get("deposited").asLong()).isZero();
        assertThat(qBefore.get("amountPaid").asLong()).isGreaterThan(qBefore.get("tds").asLong());

        // Shape checks: a BSR code is seven digits, a serial five.
        Map<String, Object> bad = new HashMap<>(Map.of("bsrCode", "63900", "depositDate", month.plusMonths(1).atDay(7).toString(),
                "challanSerial", "07259"));
        putJson("/api/v1/tax/deposits/challans/" + month, owner, bad).andExpect(status().isBadRequest());
        bad.put("bsrCode", "6390009");
        bad.put("depositDate", month.minusMonths(1).atDay(7).toString());
        putJson("/api/v1/tax/deposits/challans/" + month, owner, bad)
                .andExpect(status().isBadRequest()); // paid before the tax was even deducted

        String paidOn = month.plusMonths(1).atDay(7).isAfter(java.time.LocalDate.now())
                ? java.time.LocalDate.now().toString() : month.plusMonths(1).atDay(7).toString();
        putJson("/api/v1/tax/deposits/challans/" + month, owner,
                Map.of("bsrCode", "639 0009", "depositDate", paidOn, "challanSerial", "07259"))
                .andExpect(status().isOk());
        putJson("/api/v1/tax/deposits/receipts/" + quarter, owner, Map.of("receiptNo", "fxduopug"))
                .andExpect(status().isOk());

        JsonNode after = monthRow(getJson("/api/v1/tax/deposits?year=" + fy, owner), month.toString());
        assertThat(after.get("bsrCode").asText()).isEqualTo("6390009");
        assertThat(after.get("amount").asLong()).as("no amount given: what payroll deducted").isEqualTo(deducted);

        JsonNode form = getJson("/api/v1/tax/me/form130?year=" + fy, owner);
        JsonNode q = form.get("quarters").get(quarterIndex);
        assertThat(q.get("receiptNo").asText()).isEqualTo("FXDUOPUG");
        assertThat(q.get("deposited").asLong()).isEqualTo(q.get("tds").asLong());
        JsonNode challan = null;
        for (JsonNode c : form.get("challans")) if (month.toString().equals(c.get("month").asText())) challan = c;
        assertThat(challan).isNotNull();
        assertThat(challan.get("bsrCode").asText()).isEqualTo("6390009");
        assertThat(challan.get("challanSerial").asText()).isEqualTo("07259");
        assertThat(challan.get("depositDate").asText()).isEqualTo(paidOn);
    }

    @Test
    @DisplayName("the computation explains itself: salary heads add up, claims and ceilings are on each line")
    void computation_explains_itself() throws Exception {
        YearMonth month = YearMonth.now().minusMonths(1);
        Session owner = setUp(month.toString());
        putJson("/api/v1/tax/me/declaration", owner, Map.of("regime", "OLD",
                "items", java.util.List.of(Map.of("key", "PPF", "amount", 200000))))
                .andExpect(status().isOk());

        JsonNode c = getJson("/api/v1/tax/me/computation", owner);
        for (JsonNode m : c.get("months")) {
            if (m.get("heads").isEmpty()) continue;
            long sum = 0;
            for (JsonNode h : m.get("heads")) sum += Math.round(h.get("amount").asDouble() * 100);
            assertThat(sum).as("heads of " + m.get("month").asText() + " add up to its gross")
                    .isEqualTo(Math.round(m.get("gross").asDouble() * 100));
        }
        assertThat(c.get("months").toString()).contains("\"source\":\"LOCKED\"");

        JsonNode ppf = null;
        for (JsonNode d : c.get("deductions")) if ("PPF".equals(d.get("key").asText())) ppf = d;
        assertThat(ppf).isNotNull();
        assertThat(ppf.get("claimed").asLong()).isEqualTo(200000);
        assertThat(ppf.get("limit").asLong()).isEqualTo(150000);
        assertThat(ppf.get("allowed").asLong())
                .isEqualTo(150000 - ppf.get("usedBefore").asLong());
        assertThat(c.has("priorTds")).isTrue();
        assertThat(c.get("interestMovedToHouse").asLong()).isZero();
    }

    @Test
    @DisplayName("the signer and the employee's address reach the forms; the register is HR's alone")
    void signer_address_and_access() throws Exception {
        YearMonth month = YearMonth.now().minusMonths(1);
        Session owner = setUp(month.toString());

        putJson("/api/v1/tax/settings", owner, Map.of("declarationsOpen", true, "proofsOpen", false,
                "signerName", "Asha Rao", "signerParent", "Mohan Rao", "signerDesignation", "Director",
                "signerPlace", "Pune", "citTdsAddress", "CIT (TDS), Pune")).andExpect(status().isOk());
        putJson("/api/v1/tax/me/declaration", owner, Map.of("employeeAddress", "12 MG Road, Pune 411001"))
                .andExpect(status().isOk());

        JsonNode form = getJson("/api/v1/tax/me/form130", owner);
        assertThat(form.get("signer").get("name").asText()).isEqualTo("Asha Rao");
        assertThat(form.get("signer").get("parent").asText()).isEqualTo("Mohan Rao");
        assertThat(form.get("citTdsAddress").asText()).isEqualTo("CIT (TDS), Pune");
        assertThat(form.get("employeeAddress").asText()).isEqualTo("12 MG Road, Pune 411001");
        assertThat(getJson("/api/v1/tax/me/declaration", owner).get("employeeAddress").asText())
                .isEqualTo("12 MG Road, Pune 411001");

        // Saving only the windows leaves the signer alone.
        putJson("/api/v1/tax/settings", owner, Map.of("declarationsOpen", false, "proofsOpen", false))
                .andExpect(status().isOk());
        assertThat(getJson("/api/v1/tax/settings", owner).get("signerName").asText()).isEqualTo("Asha Rao");

        Session priya = login("priya.nair@northwind.demo", PW);
        mockMvc.perform(get("/api/v1/tax/deposits").header("Authorization", "Bearer " + priya.accessToken()))
                .andExpect(status().isForbidden());
        putJson("/api/v1/tax/deposits/receipts/2026-27-Q1", priya, Map.of("receiptNo", "ABCDEFGH"))
                .andExpect(status().isForbidden());
    }
}
