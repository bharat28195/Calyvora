package com.calyvora.tax;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The declaration end to end (PD-60): rent becomes an HRA exemption by the rules, proofs go to HR,
 * and after the proof deadline only what HR accepted reduces the tax on the payslip.
 *
 * <p>The figures are worked by hand. Salary ₹12,00,000 a year (₹1,00,000 a month); the default
 * template makes basic 50% and HRA 25% of it, so ₹50,000 and ₹25,000 a month.
 */
class TaxDeclarationWorkflowTest extends IntegrationTestBase {

    private static final String PW = "password1234";
    private static final String YEAR = "2026-27";

    private Session owner;
    private String employeeId;

    /** A company withholding income tax through Orbit, its owner on ₹12 lakh from before the year. */
    private void company() throws Exception {
        owner = onboardOwner("Acme", "owner@acme.com", PW);
        platformOwner.ensurePlatformOwner();
        Session platform = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        String companyId = null;
        for (JsonNode c : getJson("/api/v1/platform/companies", platform)) {
            if ("Acme".equals(c.get("name").asText())) companyId = c.get("companyId").asText();
        }
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/features")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("feature", "INCOME_TAX", "enabled", true))))
                .andExpect(status().isOk());
        employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();
        send(post("/api/v1/people/employees/" + employeeId + "/compensation"),
                Map.of("annualAmount", 1_200_000, "effectiveDate", "2026-01-01")).andExpect(status().isOk());
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               Object body) throws Exception {
        return mockMvc.perform(req.header("Authorization", "Bearer " + owner.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private JsonNode body(ResultActions r) throws Exception {
        MvcResult res = r.andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private JsonNode declare(Map<String, Object> payload) throws Exception {
        return body(send(put("/api/v1/tax/me/declaration").param("year", YEAR), payload));
    }

    private JsonNode computation() throws Exception {
        return getJson("/api/v1/tax/me/computation?year=" + YEAR, owner);
    }

    private long tdsFor(String month) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/people/employees/" + employeeId + "/payslip").param("month", month)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("incomeTax").asLong();
    }

    private void settings(boolean proofsOpen, String deadline) throws Exception {
        java.util.Map<String, Object> s = new java.util.HashMap<>();
        s.put("declarationsOpen", true);
        s.put("proofsOpen", proofsOpen);
        s.put("proofDeadline", deadline);
        send(put("/api/v1/tax/settings"), s).andExpect(status().isOk());
    }

    @Test
    void rent_becomes_an_hra_exemption_worked_by_the_rules() throws Exception {
        company();
        // Mumbai, ₹30,000 a month all year, old regime.
        //   Each month: HRA 25,000; rent - 10% basic = 25,000; 50% basic = 25,000 -> 25,000. Year: 3,00,000.
        //   12,00,000 - 3,00,000 - 50,000 = 8,50,000 taxable.
        //   12,500 + 3,50,000 @20% 70,000 = 82,500; cess 3,300 -> 85,800.
        declare(Map.of("regime", "OLD", "rent", List.of(Map.of("fromMonth", "2026-04", "toMonth", "2027-03",
                "monthlyRent", 30000, "city", "Mumbai", "landlordName", "R. Sharma", "landlordPan", "ABCPS1234K"))));
        JsonNode c = computation();
        assertThat(c.get("exemptions").get(0).get("allowed").asLong()).isEqualTo(300000);
        assertThat(c.get("taxableIncome").asLong()).isEqualTo(850000);
        assertThat(c.get("totalTax").asLong()).isEqualTo(85800);
        assertThat(c.get("hraMonths")).hasSize(12);
        // And the payslip withholds a twelfth of it: 85,800 / 12 = 7,150.
        assertThat(tdsFor("2026-10")).isEqualTo(7150);
    }

    @Test
    void submitting_rent_over_a_lakh_needs_the_landlords_pan() throws Exception {
        company();
        declare(Map.of("regime", "OLD", "rent", List.of(Map.of("fromMonth", "2026-04", "toMonth", "2027-03",
                "monthlyRent", 20000, "city", "Pune", "landlordName", "A. Kulkarni"))));
        send(post("/api/v1/tax/me/declaration/submit").param("year", YEAR), Map.of())
                .andExpect(status().isBadRequest());
        declare(Map.of("rent", List.of(Map.of("fromMonth", "2026-04", "toMonth", "2027-03",
                "monthlyRent", 20000, "city", "Pune", "landlordName", "A. Kulkarni", "landlordPan", "AKLPK5678Q"))));
        send(post("/api/v1/tax/me/declaration/submit").param("year", YEAR), Map.of()).andExpect(status().isOk());
    }

    @Test
    void after_the_deadline_only_accepted_proofs_reduce_the_tax() throws Exception {
        company();
        declare(Map.of("regime", "OLD", "items", List.of(Map.of("key", "PPF", "amount", 150000))));
        settings(true, "2026-12-31");
        long withClaim = tdsFor("2026-10");

        // The deadline passes before October: an unproved PPF claim stops counting.
        settings(true, "2026-09-30");
        long unproved = tdsFor("2026-10");
        assertThat(unproved).isGreaterThan(withClaim);

        // The employee uploads the passbook; HR accepts it; the claim counts again.
        mockMvc.perform(multipart("/api/v1/tax/me/proofs").file(new MockMultipartFile("file", "ppf.pdf",
                                "application/pdf", "%PDF-1.4 test".getBytes()))
                        .param("year", YEAR).param("ownerType", "ITEM").param("ownerId", "PPF")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        JsonNode list = getJson("/api/v1/tax/declarations?year=" + YEAR, owner);
        assertThat(list.get(0).get("awaitingReview").asInt()).isEqualTo(1);

        // Rejecting needs a reason the employee can act on.
        send(post("/api/v1/tax/declarations/" + employeeId + "/review").param("year", YEAR),
                Map.of("type", "ITEM", "id", "PPF", "status", "REJECTED")).andExpect(status().isBadRequest());
        send(post("/api/v1/tax/declarations/" + employeeId + "/review").param("year", YEAR),
                Map.of("type", "ITEM", "id", "PPF", "status", "ACCEPTED")).andExpect(status().isOk());
        assertThat(tdsFor("2026-10")).isEqualTo(withClaim);

        // A part-acceptance counts only the part.
        send(post("/api/v1/tax/declarations/" + employeeId + "/review").param("year", YEAR),
                Map.of("type", "ITEM", "id", "PPF", "status", "PARTIAL", "acceptedAmount", 50000, "note", "One receipt missing"))
                .andExpect(status().isOk());
        long partial = tdsFor("2026-10");
        assertThat(partial).isBetween(withClaim, unproved);
    }

    @Test
    void a_proof_needs_the_window_open_and_a_sensible_file() throws Exception {
        company();
        declare(Map.of("regime", "OLD", "items", List.of(Map.of("key", "LIFE_INSURANCE", "amount", 40000))));
        mockMvc.perform(multipart("/api/v1/tax/me/proofs").file(new MockMultipartFile("file", "lic.pdf",
                                "application/pdf", "%PDF".getBytes()))
                        .param("year", YEAR).param("ownerType", "ITEM").param("ownerId", "LIFE_INSURANCE")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest());   // proofs not open yet
        settings(true, null);
        mockMvc.perform(multipart("/api/v1/tax/me/proofs").file(new MockMultipartFile("file", "lic.exe",
                                "application/x-msdownload", "MZ".getBytes()))
                        .param("year", YEAR).param("ownerType", "ITEM").param("ownerId", "LIFE_INSURANCE")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest());   // not a PDF or an image
    }

    @Test
    void the_preview_prices_an_unsaved_form() throws Exception {
        company();
        JsonNode saved = computation();
        JsonNode preview = body(send(post("/api/v1/tax/me/preview").param("year", YEAR),
                Map.of("regime", "OLD", "items", List.of(Map.of("key", "ELSS", "amount", 150000)))));
        assertThat(preview.get("regime").asText()).isEqualTo("OLD");
        assertThat(preview.get("totalDeductions").asLong()).isEqualTo(150000);
        // Nothing was saved.
        assertThat(computation().get("regime").asText()).isEqualTo(saved.get("regime").asText());
    }

    @Test
    void a_changed_amount_goes_back_for_review() throws Exception {
        company();
        declare(Map.of("regime", "OLD", "items", List.of(Map.of("key", "PPF", "amount", 50000))));
        settings(true, null);
        mockMvc.perform(multipart("/api/v1/tax/me/proofs").file(new MockMultipartFile("file", "ppf.pdf",
                                "application/pdf", "%PDF".getBytes()))
                        .param("year", YEAR).param("ownerType", "ITEM").param("ownerId", "PPF")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        send(post("/api/v1/tax/declarations/" + employeeId + "/review").param("year", YEAR),
                Map.of("type", "ITEM", "id", "PPF", "status", "ACCEPTED")).andExpect(status().isOk());
        JsonNode after = declare(Map.of("items", List.of(Map.of("key", "PPF", "amount", 150000))));
        assertThat(after.get("items").get(0).get("proofStatus").asText()).isEqualTo("SUBMITTED");
    }

    @Test
    void form_130_part_b_carries_the_employer_and_the_working() throws Exception {
        company();
        JsonNode f = getJson("/api/v1/tax/me/form130?year=" + YEAR, owner);
        assertThat(f.get("employerName").asText()).isNotBlank();
        assertThat(f.get("quarters")).hasSize(4);
        assertThat(f.get("computation").get("totalTax").isNumber()).isTrue();
        assertThat(f.get("periodFrom").asText()).isEqualTo("2026-04-01");
    }

    @Test
    void a_previous_employers_income_and_tds_join_the_year() throws Exception {
        company();
        long before = computation().get("totalTax").asLong();
        declare(Map.of("previous", Map.of("employerName", "Old Co", "income", 600000, "tds", 20000)));
        JsonNode c = computation();
        assertThat(c.get("previousEmployerIncome").asLong()).isEqualTo(600000);
        assertThat(c.get("totalTax").asLong()).isGreaterThan(before);
    }
}
