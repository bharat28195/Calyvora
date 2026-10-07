package com.calyvora.payroll;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Finalising a payroll month (V65): once a month is locked its payslips are read back as issued,
 * whatever changes afterwards — and later months' income tax knows what was actually withheld.
 */
class PayrollLockIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private String employeeOnSalary(Session owner, int annual, String effective) throws Exception {
        String employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();
        raise(owner, employeeId, annual, effective);
        return employeeId;
    }

    private void raise(Session owner, String employeeId, int annual, String effective) throws Exception {
        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/compensation")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("annualAmount", annual, "effectiveDate", effective))))
                .andExpect(status().isOk());
    }

    private JsonNode payslip(Session owner, String employeeId, String month) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/people/employees/" + employeeId + "/payslip")
                        .param("month", month)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode finalizeMonth(Session owner, String month) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/payroll/months/" + month + "/finalize")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private void turnOnIncomeTax(String companyName) throws Exception {
        platformOwner.ensurePlatformOwner();
        Session platform = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        String companyId = null;
        for (JsonNode c : getJson("/api/v1/platform/companies", platform)) {
            if (companyName.equals(c.get("name").asText())) companyId = c.get("companyId").asText();
        }
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/features")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("feature", "INCOME_TAX", "enabled", true))))
                .andExpect(status().isOk());
    }

    @Test
    void a_finalised_payslip_does_not_change_when_the_salary_does() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 600_000, "2026-01-01");   // 50,000 a month

        JsonNode locked = finalizeMonth(owner, "2026-05");
        assertThat(locked.get("finalized").asBoolean()).isTrue();
        assertThat(locked.get("employees").asInt()).isEqualTo(1);

        raise(owner, employeeId, 1_200_000, "2026-06-01");

        JsonNode may = payslip(owner, employeeId, "2026-05");
        assertThat(may.get("finalized").asBoolean()).isTrue();
        assertThat(may.get("gross").decimalValue().intValue()).isEqualTo(50_000);

        JsonNode june = payslip(owner, employeeId, "2026-06");
        assertThat(june.get("finalized").asBoolean()).isFalse();
        assertThat(june.get("gross").decimalValue().intValue()).isEqualTo(100_000);

        JsonNode run = getJson("/api/v1/payroll/run?month=2026-05", owner);
        assertThat(run.get("totalGross").decimalValue().intValue()).isEqualTo(50_000);
    }

    @Test
    void a_month_can_be_finalised_once() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        employeeOnSalary(owner, 600_000, "2026-01-01");
        finalizeMonth(owner, "2026-05");
        mockMvc.perform(post("/api/v1/payroll/months/2026-05/finalize")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void a_future_month_cannot_be_finalised() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        employeeOnSalary(owner, 600_000, "2026-01-01");
        mockMvc.perform(post("/api/v1/payroll/months/2099-01/finalize")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void only_the_latest_finalised_month_can_be_reopened() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 600_000, "2026-01-01");
        finalizeMonth(owner, "2026-05");
        finalizeMonth(owner, "2026-06");

        mockMvc.perform(post("/api/v1/payroll/months/2026-05/reopen")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/payroll/months/2026-06/reopen")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        assertThat(payslip(owner, employeeId, "2026-06").get("finalized").asBoolean()).isFalse();
        assertThat(getJson("/api/v1/payroll/months", owner).size()).isEqualTo(1);
    }

    @Test
    void later_months_tds_corrects_for_what_was_actually_withheld() throws Exception {
        // April and May are locked at the old salary; a raise from June means the rest of the year
        // must withhold more than an even twelfth of the NEW annual tax, to make up the shortfall.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 1_500_000, "2026-01-01");
        turnOnIncomeTax("Acme");

        int aprilTds = payslip(owner, employeeId, "2026-04").get("incomeTax").decimalValue().intValue();
        assertThat(aprilTds).as("tax is withheld at 15 lakh").isPositive();
        finalizeMonth(owner, "2026-04");
        finalizeMonth(owner, "2026-05");

        raise(owner, employeeId, 2_400_000, "2026-06-01");
        int june = payslip(owner, employeeId, "2026-06").get("incomeTax").decimalValue().intValue();

        // The year's income is what April and May actually paid (1,25,000 each) plus ten months at the
        // new 2,00,000; the tax on it, less what April and May withheld, is spread over the ten months
        // that remain. A plain twelfth of the new salary's tax would leave the shortfall for March.
        var newYear = com.calyvora.tax.IncomeTaxCalculator.compute(com.calyvora.tax.IncomeTaxCalculator.Input
                .of(java.math.BigDecimal.valueOf(2 * 125_000 + 10 * 200_000), com.calyvora.tax.TaxRegime.DEFAULT));
        int expected = newYear.totalTax().subtract(java.math.BigDecimal.valueOf(2L * aprilTds))
                .divide(java.math.BigDecimal.TEN, 0, java.math.RoundingMode.HALF_UP).intValue();
        assertThat(june).isEqualTo(expected);
        assertThat(june).as("more than an even twelfth, to make up April and May")
                .isGreaterThan(newYear.monthlyTds().intValue());

        // And the employee's tax screen says the same as the payslip for the month being paid.
        assertThat(payslip(owner, employeeId, "2026-06").get("incomeTax").decimalValue().intValue()).isEqualTo(june);

        // The locked months themselves never move.
        assertThat(payslip(owner, employeeId, "2026-04").get("incomeTax").decimalValue().intValue())
                .isEqualTo(aprilTds);
    }

    @Test
    void a_member_cannot_finalise_payroll() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        employeeOnSalary(owner, 600_000, "2026-01-01");
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "member@acme.com", "role", "MEMBER"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", email().lastInvitationToken(), "firstName", "Mem",
                                "lastName", "Ber", "password", PW))))
                .andExpect(status().isOk());
        Session member = login("member@acme.com", PW);

        mockMvc.perform(post("/api/v1/payroll/months/2026-05/finalize")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isForbidden());
    }
}
