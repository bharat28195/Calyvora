package com.calyvora.payroll;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provident Fund, and the switch that keeps it off until a customer's numbers have been checked.
 *
 * <p>The arithmetic is proved in {@link PfCalculatorTest}. What only a real request can show is the
 * thing that actually matters commercially: that <b>nothing changes for a company until the vendor
 * turns it on</b>, and that turning it on for one customer does not turn it on for another.
 */
class StatutoryPayrollIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private Session platformOwner() throws Exception {
        platformOwner.ensurePlatformOwner();
        return login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
    }

    /** The company id, as the platform console sees it. */
    private String companyIdOf(Session owner, String companyName) throws Exception {
        JsonNode companies = getJson("/api/v1/platform/companies", owner);
        for (JsonNode c : companies) {
            if (companyName.equals(c.get("name").asText())) {
                return c.get("companyId").asText();
            }
        }
        throw new AssertionError("no company called " + companyName + " in " + companies);
    }

    private void setFeature(Session platform, String companyId, boolean enabled) throws Exception {
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/features")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("feature", "STATUTORY_PAYROLL", "enabled", enabled))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(enabled));
    }

    /** An employee on a known salary, enrolled in PF. Returns their employee id. */
    private String employeeOnSalary(Session owner, int annual) throws Exception {
        JsonNode people = getJson("/api/v1/people/employees", owner);
        String employeeId = people.get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/compensation")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("annualAmount", annual, "effectiveDate", "2026-01-01",
                                "reason", "initial"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/people/employees/" + employeeId + "/finance")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pfStatus", "ENABLED", "uan", "100200300400"))))
                .andExpect(status().isOk());
        return employeeId;
    }

    private JsonNode payslip(Session owner, String employeeId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/people/employees/" + employeeId + "/payslip")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void pf_is_off_until_the_vendor_turns_it_on() throws Exception {
        // The whole promise of the flag: a company that has never been touched behaves exactly as it
        // did before statutory payroll existed.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 1_200_000);

        JsonNode before = payslip(owner, employeeId);
        assertThat(before.get("statutory").isNull())
                .as("no statutory block at all, not a block of zeroes")
                .isTrue();
        assertThat(before.get("deductions").toString()).doesNotContain("Provident Fund");
    }

    @Test
    void turning_it_on_deducts_pf_and_reports_the_employer_cost() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 1_200_000);   // 100,000/month gross
        Session platform = platformOwner();
        setFeature(platform, companyIdOf(platform, "Acme"), true);

        JsonNode after = payslip(owner, employeeId);
        JsonNode statutory = after.get("statutory");

        assertThat(statutory.isNull()).isFalse();
        // Default template makes Basic 50% of gross = 50,000; the ceiling caps PF wages at 15,000.
        assertThat(statutory.get("pfWages").decimalValue().intValue()).isEqualTo(15_000);
        assertThat(statutory.get("employeePf").decimalValue().intValue()).isEqualTo(1_800);
        assertThat(statutory.get("employerEps").decimalValue().intValue()).isEqualTo(1_250);
        assertThat(statutory.get("employerEpf").decimalValue().intValue()).isEqualTo(550);

        // And it is a real deduction, inside net — not a note on the side.
        assertThat(after.get("deductions").toString()).contains("Provident Fund");
    }

    @Test
    void the_employee_deduction_actually_reduces_net_pay() throws Exception {
        // The failure this pins: showing PF on the payslip while paying the person as if it were not
        // deducted. The number would look right and the bank transfer would be wrong.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 1_200_000);

        int netBefore = payslip(owner, employeeId).get("net").decimalValue().intValue();

        Session platform = platformOwner();
        setFeature(platform, companyIdOf(platform, "Acme"), true);

        JsonNode after = payslip(owner, employeeId);
        int netAfter = after.get("net").decimalValue().intValue();
        int pf = after.get("statutory").get("employeePf").decimalValue().intValue();

        assertThat(netBefore - netAfter).isEqualTo(pf);
    }

    @Test
    void an_employee_who_is_not_enrolled_gets_no_deduction_even_when_the_feature_is_on() throws Exception {
        // Company-level and employee-level are independent switches, and neither implies the other.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        JsonNode people = getJson("/api/v1/people/employees", owner);
        String employeeId = people.get(0).get("id").asText();
        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/compensation")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("annualAmount", 1_200_000, "effectiveDate", "2026-01-01"))))
                .andExpect(status().isOk());
        // pfStatus left at its NOT_ELIGIBLE default.

        Session platform = platformOwner();
        setFeature(platform, companyIdOf(platform, "Acme"), true);

        assertThat(payslip(owner, employeeId).get("statutory").isNull()).isTrue();
    }

    @Test
    void turning_it_on_for_one_company_leaves_another_alone() throws Exception {
        // A flag that leaked across tenants would be worse than no flag: it would deduct money from
        // the salaries of a customer who never asked for it.
        Session acme = onboardOwner("Acme", "owner@acme.com", PW);
        String acmeEmployee = employeeOnSalary(acme, 1_200_000);
        Session other = onboardOwner("Beta Corp", "owner@beta.com", PW);
        String betaEmployee = employeeOnSalary(other, 1_200_000);

        Session platform = platformOwner();
        setFeature(platform, companyIdOf(platform, "Acme"), true);

        assertThat(payslip(acme, acmeEmployee).get("statutory").isNull()).isFalse();
        assertThat(payslip(other, betaEmployee).get("statutory").isNull())
                .as("Beta Corp never asked for statutory payroll")
                .isTrue();
    }

    @Test
    void turning_it_back_off_stops_the_deduction() throws Exception {
        // "Turn it off for that one company while we look at it" must not need a redeploy.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 1_200_000);
        Session platform = platformOwner();
        String companyId = companyIdOf(platform, "Acme");

        setFeature(platform, companyId, true);
        assertThat(payslip(owner, employeeId).get("statutory").isNull()).isFalse();

        setFeature(platform, companyId, false);
        assertThat(payslip(owner, employeeId).get("statutory").isNull()).isTrue();
    }

    @Test
    void a_company_can_read_its_own_flags_but_not_set_them() throws Exception {
        // The switch is what the vendor sells; the customer sees the state so a screen can explain
        // itself, and cannot flip it.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);

        JsonNode features = getJson("/api/v1/company/features", owner);
        assertThat(features.toString()).contains("STATUTORY_PAYROLL");

        mockMvc.perform(post("/api/v1/platform/companies/" + companyIdOf(platformOwner(), "Acme") + "/features")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("feature", "STATUTORY_PAYROLL", "enabled", true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void pf_rates_are_a_setting_and_the_ceiling_can_move() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 1_200_000);
        Session platform = platformOwner();
        setFeature(platform, companyIdOf(platform, "Acme"), true);

        mockMvc.perform(patch("/api/v1/payroll/pf-settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("wageCeiling", 21000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        JsonNode statutory = payslip(owner, employeeId).get("statutory");
        assertThat(statutory.get("pfWages").decimalValue().intValue()).isEqualTo(21_000);
        assertThat(statutory.get("employeePf").decimalValue().intValue()).isEqualTo(2_520);
    }

    @Test
    void an_eps_rate_above_the_employer_rate_is_refused() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);

        mockMvc.perform(patch("/api/v1/payroll/pf-settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("epsRate", 20))))
                .andExpect(status().isBadRequest());
    }

    // ---- ESI and professional tax (V64) ---------------------------------------------------------

    private void patchFinance(Session owner, String employeeId, Map<String, Object> body) throws Exception {
        mockMvc.perform(patch("/api/v1/people/employees/" + employeeId + "/finance")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    private void statutorySettings(Session owner, Map<String, Object> body) throws Exception {
        mockMvc.perform(patch("/api/v1/payroll/statutory-settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    private JsonNode payslipFor(Session owner, String employeeId, String month) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/people/employees/" + employeeId + "/payslip")
                        .param("month", month)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void esi_is_deducted_for_an_eligible_employee_under_the_ceiling() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 216_000);   // 18,000 a month
        patchFinance(owner, employeeId, Map.of("esiStatus", "ELIGIBLE"));
        statutorySettings(owner, Map.of("esiEnabled", true));
        Session platform = platformOwner();
        setFeature(platform, companyIdOf(platform, "Acme"), true);

        JsonNode slip = payslipFor(owner, employeeId, "2026-11");
        JsonNode st = slip.get("statutory");
        assertThat(st.get("employeeEsi").decimalValue().intValue()).as("0.75% of 18,000").isEqualTo(135);
        assertThat(st.get("employerEsi").decimalValue().intValue()).as("3.25% of 18,000").isEqualTo(585);
        assertThat(slip.get("deductions").toString()).contains("ESI (employee)");
        // PF (enrolled by employeeOnSalary) and ESI employer shares together.
        int pfEmployer = st.get("employerEps").decimalValue().intValue() + st.get("employerEpf").decimalValue().intValue()
                + st.get("employerAdminCharges").decimalValue().intValue() + st.get("employerEdli").decimalValue().intValue();
        assertThat(st.get("employerTotal").decimalValue().intValue()).isEqualTo(pfEmployer + 585);
    }

    @Test
    void esi_does_not_apply_above_the_ceiling_and_says_so_with_null_not_zero() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 1_200_000);
        patchFinance(owner, employeeId, Map.of("esiStatus", "ELIGIBLE"));
        statutorySettings(owner, Map.of("esiEnabled", true));
        Session platform = platformOwner();
        setFeature(platform, companyIdOf(platform, "Acme"), true);

        JsonNode st = payslipFor(owner, employeeId, "2026-11").get("statutory");
        assertThat(st.get("employeeEsi").isNull()).isTrue();
        assertThat(st.get("employerEsi").isNull()).isTrue();
    }

    @Test
    void professional_tax_follows_the_employees_state() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 1_200_000);
        patchFinance(owner, employeeId, Map.of("ptState", "Karnataka"));
        statutorySettings(owner, Map.of("ptEnabled", true));
        Session platform = platformOwner();
        setFeature(platform, companyIdOf(platform, "Acme"), true);

        JsonNode nov = payslipFor(owner, employeeId, "2026-11");
        assertThat(nov.get("statutory").get("professionalTax").decimalValue().intValue()).isEqualTo(200);
        assertThat(nov.get("statutory").get("ptState").asText()).isEqualTo("KA");
        assertThat(payslipFor(owner, employeeId, "2027-02").get("statutory").get("professionalTax")
                .decimalValue().intValue()).as("Karnataka collects 300 in February").isEqualTo(300);

        int netBefore = nov.get("gross").decimalValue().intValue()
                - nov.get("totalDeductions").decimalValue().intValue();
        assertThat(nov.get("net").decimalValue().intValue()).isEqualTo(netBefore);
    }

    @Test
    void esi_and_pt_switches_do_nothing_until_the_vendor_turns_statutory_on() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 216_000);
        patchFinance(owner, employeeId, Map.of("esiStatus", "ELIGIBLE", "ptState", "Karnataka"));
        statutorySettings(owner, Map.of("esiEnabled", true, "ptEnabled", true));

        assertThat(payslipFor(owner, employeeId, "2026-11").get("statutory").isNull()).isTrue();
        JsonNode settings = getJson("/api/v1/payroll/statutory-settings", owner);
        assertThat(settings.get("statutoryEnabled").asBoolean()).isFalse();
        assertThat(settings.get("esiEnabled").asBoolean()).isTrue();
    }

    @Test
    void registration_numbers_are_validated_and_normalised() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        mockMvc.perform(patch("/api/v1/payroll/statutory-settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tan", "not-a-tan"))))
                .andExpect(status().isBadRequest());

        statutorySettings(owner, Map.of("tan", "dela12345b", "companyPan", "abcde1234f",
                "pfEstablishmentCode", "mhban0012345000"));
        JsonNode s = getJson("/api/v1/payroll/statutory-settings", owner);
        assertThat(s.get("tan").asText()).isEqualTo("DELA12345B");
        assertThat(s.get("companyPan").asText()).isEqualTo("ABCDE1234F");
        assertThat(s.get("pfEstablishmentCode").asText()).isEqualTo("MHBAN0012345000");
    }

    @Test
    void the_run_carries_esi_and_professional_tax_per_person() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employeeOnSalary(owner, 216_000);
        patchFinance(owner, employeeId, Map.of("esiStatus", "ELIGIBLE", "ptState", "West Bengal"));
        statutorySettings(owner, Map.of("esiEnabled", true, "ptEnabled", true));
        Session platform = platformOwner();
        setFeature(platform, companyIdOf(platform, "Acme"), true);

        JsonNode run = getJson("/api/v1/payroll/run?month=2026-11", owner);
        JsonNode row = run.get("rows").get(0);
        assertThat(row.get("employeeEsi").decimalValue().intValue()).isEqualTo(135);
        assertThat(row.get("professionalTax").decimalValue().intValue()).as("West Bengal, 15,001–25,000").isEqualTo(130);
    }

    @Test
    void the_payroll_run_reports_what_the_month_costs_the_employer() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        employeeOnSalary(owner, 1_200_000);
        Session platform = platformOwner();
        setFeature(platform, companyIdOf(platform, "Acme"), true);

        JsonNode run = getJson("/api/v1/payroll/run", owner);
        assertThat(run.get("totalEmployerContribution").decimalValue().intValue())
                .as("EPS 1250 + EPF 550 + admin 75 + EDLI 75")
                .isEqualTo(1_950);
    }
}
