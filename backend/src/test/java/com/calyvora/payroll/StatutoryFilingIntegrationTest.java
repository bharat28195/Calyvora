package com.calyvora.payroll;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The statutory return files — ECR, ESI, professional tax, Form 24Q — and the readiness check,
 * all built from finalised months.
 */
class StatutoryFilingIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private String companyIdOf(Session platform, String name) throws Exception {
        for (JsonNode c : getJson("/api/v1/platform/companies", platform)) {
            if (name.equals(c.get("name").asText())) return c.get("companyId").asText();
        }
        throw new AssertionError("no company " + name);
    }

    private void feature(String company, String feature) throws Exception {
        platformOwner.ensurePlatformOwner();
        Session platform = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        mockMvc.perform(post("/api/v1/platform/companies/" + companyIdOf(platform, company) + "/features")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("feature", feature, "enabled", true))))
                .andExpect(status().isOk());
    }

    private void send(Session owner, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                      Map<String, Object> body) throws Exception {
        mockMvc.perform(req.header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }

    /** An employee on a salary with the given finance fields, statutory payroll on. */
    private String setUp(Session owner, int annual, Map<String, Object> finance) throws Exception {
        String employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();
        send(owner, post("/api/v1/people/employees/" + employeeId + "/compensation"),
                Map.of("annualAmount", annual, "effectiveDate", "2026-01-01"));
        send(owner, patch("/api/v1/people/employees/" + employeeId + "/finance"), finance);
        return employeeId;
    }

    private void finalizeMonth(Session owner, String month) throws Exception {
        mockMvc.perform(post("/api/v1/payroll/months/" + month + "/finalize")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    private JsonNode filing(Session owner, String path) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/payroll/filings/" + path).param("format", "json")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void the_ecr_has_one_eleven_field_line_per_pf_member() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        setUp(owner, 1_200_000, Map.of("pfStatus", "ENABLED", "uan", "100200300400"));
        feature("Acme", "STATUTORY_PAYROLL");
        finalizeMonth(owner, "2026-05");

        JsonNode file = filing(owner, "2026-05/ecr");
        String[] lines = file.get("content").asText().trim().split("\n");
        assertThat(lines).hasSize(1);
        String[] f = lines[0].split("#~#", -1);
        assertThat(f).hasSize(11);
        assertThat(f[0]).isEqualTo("100200300400");
        assertThat(f[1]).matches("[A-Z .]+");
        assertThat(f[2]).as("gross wages").isEqualTo("100000");
        assertThat(f[3]).as("EPF wages, capped").isEqualTo("15000");
        assertThat(f[4]).as("EPS wages").isEqualTo("15000");
        assertThat(f[5]).as("EDLI wages").isEqualTo("15000");
        assertThat(f[6]).as("employee EPF").isEqualTo("1800");
        assertThat(f[7]).as("EPS").isEqualTo("1250");
        assertThat(f[8]).as("EPF − EPS").isEqualTo("550");
        assertThat(f[9]).as("NCP days").isEqualTo("0");

        // The plain download is the same text.
        MvcResult raw = mockMvc.perform(get("/api/v1/payroll/filings/2026-05/ecr")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk()).andReturn();
        assertThat(raw.getResponse().getHeader("Content-Disposition")).contains("ECR_2026-05.txt");
        assertThat(raw.getResponse().getContentAsString()).isEqualTo(file.get("content").asText());
    }

    @Test
    void a_member_without_a_uan_is_left_out_and_named() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        setUp(owner, 1_200_000, Map.of("pfStatus", "ENABLED"));
        feature("Acme", "STATUTORY_PAYROLL");
        finalizeMonth(owner, "2026-05");

        JsonNode file = filing(owner, "2026-05/ecr");
        assertThat(file.get("content").asText()).isEmpty();
        assertThat(file.get("skipped").get(0).get("reason").asText()).contains("UAN");
    }

    @Test
    void returns_need_a_finalised_month() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        setUp(owner, 1_200_000, Map.of("pfStatus", "ENABLED", "uan", "100200300400"));
        mockMvc.perform(get("/api/v1/payroll/filings/2026-05/ecr")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void the_esi_file_lists_covered_employees_with_their_wages() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        setUp(owner, 216_000, Map.of("esiStatus", "ELIGIBLE", "esiNumber", "1234567890"));
        send(owner, patch("/api/v1/payroll/statutory-settings"), Map.of("esiEnabled", true));
        feature("Acme", "STATUTORY_PAYROLL");
        finalizeMonth(owner, "2026-05");

        String[] lines = filing(owner, "2026-05/esi").get("content").asText().trim().split("\n");
        assertThat(lines[0]).startsWith("IP Number,IP Name");
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).startsWith("1234567890,").contains(",31,18000,");
    }

    @Test
    void the_pt_summary_totals_by_state() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        setUp(owner, 1_200_000, Map.of("ptState", "Karnataka"));
        send(owner, patch("/api/v1/payroll/statutory-settings"), Map.of("ptEnabled", true));
        feature("Acme", "STATUTORY_PAYROLL");
        finalizeMonth(owner, "2026-05");

        String content = filing(owner, "2026-05/pt").get("content").asText();
        assertThat(content).contains("KA,TOTAL (1 employees),,200");
    }

    @Test
    void form_24q_covers_the_quarter_and_flags_a_missing_pan() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = setUp(owner, 1_500_000, Map.of("pfStatus", "NOT_ELIGIBLE"));
        feature("Acme", "INCOME_TAX");
        for (String m : List.of("2026-04", "2026-05")) finalizeMonth(owner, m);

        // June not finalised yet: the quarter cannot be built.
        mockMvc.perform(get("/api/v1/payroll/filings/24q/2026-27-Q1").param("format", "json")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isConflict());

        finalizeMonth(owner, "2026-06");
        JsonNode file = filing(owner, "24q/2026-27-Q1");
        String[] lines = file.get("content").asText().trim().split("\n");
        assertThat(lines).hasSize(5);   // header, three months, total
        assertThat(lines[1]).contains("PANNOTAVBL").contains(",192,");
        assertThat(file.get("skipped").size()).isEqualTo(3);

        Map<String, Object> pan = new HashMap<>();
        pan.put("panNumber", "ABCDE1234F");
        send(owner, patch("/api/v1/people/employees/" + employeeId + "/finance"), pan);
        assertThat(filing(owner, "24q/2026-27-Q1").get("content").asText()).contains("ABCDE1234F");
    }

    @Test
    void readiness_names_what_will_stop_a_return() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        setUp(owner, 1_200_000, Map.of("pfStatus", "ENABLED"));
        feature("Acme", "STATUTORY_PAYROLL");

        JsonNode issues = getJson("/api/v1/payroll/filings/readiness", owner);
        String all = issues.toString();
        assertThat(all).contains("has no UAN").contains("PF establishment code is not set");

        send(owner, patch("/api/v1/payroll/statutory-settings"), Map.of("pfEstablishmentCode", "MHBAN0012345000"));
        assertThat(getJson("/api/v1/payroll/filings/readiness", owner).toString())
                .doesNotContain("PF establishment code");
    }

    @Test
    void quarters_map_to_financial_year_months() {
        assertThat(StatutoryFilingService.quarterMonths("2026-27-Q1")).containsExactly("2026-04", "2026-05", "2026-06");
        assertThat(StatutoryFilingService.quarterMonths("2026-27-Q4")).containsExactly("2027-01", "2027-02", "2027-03");
    }
}
