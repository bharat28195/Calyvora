package com.calyvora.tax;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Income tax over the whole year (V66): a mid-year joiner is taxed on the months they work, spread
 * over those months, and income already taxed before Orbit is counted rather than ignored.
 */
class TdsYearIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private void send(Session owner, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                      Map<String, Object> body) throws Exception {
        mockMvc.perform(req.header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }

    private Session companyWithIncomeTax(String name, String email) throws Exception {
        Session owner = onboardOwner(name, email, PW);
        platformOwner.ensurePlatformOwner();
        Session platform = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        String companyId = null;
        for (JsonNode c : getJson("/api/v1/platform/companies", platform)) {
            if (name.equals(c.get("name").asText())) companyId = c.get("companyId").asText();
        }
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/features")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("feature", "INCOME_TAX", "enabled", true))))
                .andExpect(status().isOk());
        return owner;
    }

    private int tds(Session owner, String employeeId, String month) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/people/employees/" + employeeId + "/payslip")
                        .param("month", month)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("incomeTax").decimalValue().intValue();
    }

    private static BigDecimal taxOn(long income) {
        return IncomeTaxCalculator.compute(IncomeTaxCalculator.Input.of(BigDecimal.valueOf(income), TaxRegime.DEFAULT))
                .totalTax();
    }

    @Test
    void a_mid_year_joiner_is_taxed_on_the_months_they_work_spread_over_those_months() throws Exception {
        Session owner = companyWithIncomeTax("Acme", "owner@acme.com");
        String employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();
        send(owner, patch("/api/v1/people/employees/" + employeeId), Map.of("startDate", "2026-10-01"));
        send(owner, post("/api/v1/people/employees/" + employeeId + "/compensation"),
                Map.of("annualAmount", 3_600_000, "effectiveDate", "2026-10-01"));   // 3,00,000 a month

        // Six months at 3,00,000 = 18 lakh this year, its tax over six months — not 36 lakh's tax over twelve.
        int expected = taxOn(1_800_000).divide(BigDecimal.valueOf(6), 0, RoundingMode.HALF_UP).intValue();
        assertThat(tds(owner, employeeId, "2026-10")).isEqualTo(expected);

        // Not employed in September: no payslip at all.
        mockMvc.perform(get("/api/v1/people/employees/" + employeeId + "/payslip").param("month", "2026-09")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void income_and_tds_from_before_orbit_are_counted() throws Exception {
        Session owner = companyWithIncomeTax("Acme", "owner@acme.com");
        String employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();
        send(owner, post("/api/v1/people/employees/" + employeeId + "/compensation"),
                Map.of("annualAmount", 2_400_000, "effectiveDate", "2026-01-01"));   // 2,00,000 a month

        // April–September ran in the old system: 12 lakh paid, 1,00,000 withheld.
        mockMvc.perform(put("/api/v1/payroll/tds-openings/" + employeeId).param("year", "2026-27")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("coveredThrough", "2026-09", "income", 1_200_000, "tds", 100_000))))
                .andExpect(status().isOk());

        // Year = 12 lakh before + six months at 2 lakh = 24 lakh. Its tax, less the 1 lakh already
        // withheld, over the six months Orbit pays.
        int expected = taxOn(2_400_000).subtract(BigDecimal.valueOf(100_000))
                .divide(BigDecimal.valueOf(6), 0, RoundingMode.HALF_UP).intValue();
        assertThat(tds(owner, employeeId, "2026-10")).isEqualTo(expected);

        JsonNode list = getJson("/api/v1/payroll/tds-openings?year=2026-27", owner);
        assertThat(list.get(0).get("coveredThrough").asText()).isEqualTo("2026-09");
    }

    @Test
    void an_opening_month_outside_the_year_is_refused() throws Exception {
        Session owner = companyWithIncomeTax("Acme", "owner@acme.com");
        String employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();
        mockMvc.perform(put("/api/v1/payroll/tds-openings/" + employeeId).param("year", "2026-27")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("coveredThrough", "2027-05", "income", 1, "tds", 0))))
                .andExpect(status().isBadRequest());
    }
}
