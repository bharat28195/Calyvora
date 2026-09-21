package com.calyvora.tax;

import com.calyvora.platform.PlatformOwnerBootstrap;
import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Income tax reaching the payslip — and, first of all, not reaching it by accident.
 *
 * <p>The most important assertion here is the one that changes nothing: a company that has not
 * switched income tax on must see exactly the payslip it saw yesterday. A deduction that appears
 * unbidden on everybody's pay is the kind of change that gets noticed by the whole company at once
 * and trusted by none of them afterwards.
 */
class TdsOnPayslipTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Autowired
    private PlatformOwnerBootstrap platformOwnerBootstrap;

    private Session demoOwner() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login("ava.chen@northwind.demo", PW);
    }

    private Session platformOwner() throws Exception {
        platformOwnerBootstrap.ensurePlatformOwner();
        return login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
    }

    private String companyIdOf(Session platform, String name) throws Exception {
        for (JsonNode c : getJson("/api/v1/platform/companies", platform)) {
            if (name.equals(c.get("name").asText())) {
                return c.get("companyId").asText();
            }
        }
        throw new AssertionError("no company called " + name);
    }

    private void setIncomeTax(Session platform, String companyId, boolean enabled) throws Exception {
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/features")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("feature", "INCOME_TAX", "enabled", enabled))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(enabled));
    }

    private JsonNode myPayslip(Session session) throws Exception {
        return getJson("/api/v1/people/me/payslip?month=" + YearMonth.now().minusMonths(1), session);
    }

    private static JsonNode lineNamed(JsonNode payslip, String label) {
        for (JsonNode line : payslip.get("deductions")) {
            if (label.equals(line.get("label").asText())) {
                return line;
            }
        }
        return null;
    }

    @Test
    @DisplayName("a company that has not switched it on sees no tax line at all")
    void off_by_default_changes_nothing() throws Exception {
        Session owner = demoOwner();
        JsonNode payslip = myPayslip(owner);

        // The feature is off unless somebody turned it on, exactly as PF is. Adding a module must
        // not quietly start withholding from every payslip in every existing company.
        assertThat(lineNamed(payslip, "Income tax (TDS)"))
                .as("no tax line until the company switches income tax on")
                .isNull();
    }

    @Test
    @DisplayName("switched on, the tax appears as a deduction and comes out of net pay")
    void the_tax_reaches_the_payslip() throws Exception {
        Session owner = demoOwner();
        JsonNode before = myPayslip(owner);
        long netBefore = before.get("net").asLong();

        Session platform = platformOwner();
        setIncomeTax(platform, companyIdOf(platform, "Northwind Robotics"), true);

        JsonNode after = myPayslip(owner);
        JsonNode tds = lineNamed(after, "Income tax (TDS)");
        assertThat(tds).as("the owner is on a taxable salary, so there must be a line").isNotNull();
        assertThat(tds.get("amount").asLong()).isPositive();

        assertThat(after.get("net").asLong())
                .as("net pay falls by exactly the tax withheld")
                .isEqualTo(netBefore - tds.get("amount").asLong());
    }

    @Test
    @DisplayName("the payslip agrees with the tax screen — a twelfth of the year's bill")
    void the_payslip_and_the_screen_agree() throws Exception {
        Session owner = demoOwner();
        Session platform = platformOwner();
        setIncomeTax(platform, companyIdOf(platform, "Northwind Robotics"), true);

        JsonNode computation = getJson("/api/v1/tax/me/computation", owner);
        JsonNode tds = lineNamed(myPayslip(owner), "Income tax (TDS)");

        assertThat(tds).isNotNull();
        // Two screens disagreeing about somebody's tax is worse than either being wrong, because
        // there is then no number to argue about.
        assertThat(tds.get("amount").asLong())
                .isEqualTo(computation.get("monthlyTds").asLong());
    }

    @Test
    @DisplayName("declaring deductions under the old regime reduces what is withheld")
    void a_declaration_moves_the_payslip() throws Exception {
        Session owner = demoOwner();
        Session platform = platformOwner();
        setIncomeTax(platform, companyIdOf(platform, "Northwind Robotics"), true);

        // Both measurements on the old regime, so this isolates the effect of the deductions.
        // Comparing a declared old-regime figure against the default new-regime one measures the two
        // regimes rather than the declaration — and on this salary the new regime wins anyway, since
        // the old one's 30% band starts at ₹10 lakh and swallows four lakh of deductions whole.
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("regime", "OLD", "declared", Map.of()))))
                .andExpect(status().isOk());
        long withheldBefore = lineNamed(myPayslip(owner), "Income tax (TDS)").get("amount").asLong();

        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("regime", "OLD",
                                "declared", Map.of("SECTION_80C", 150000,
                                        "SECTION_80CCD_1B", 50000,
                                        "HOME_LOAN_INTEREST", 200000)))))
                .andExpect(status().isOk());

        long withheldAfter = lineNamed(myPayslip(owner), "Income tax (TDS)").get("amount").asLong();
        // The point of the whole declaration flow: what somebody declares in April changes what
        // comes off their pay in May.
        assertThat(withheldAfter)
                .as("four lakh of deductions must reduce the monthly withholding")
                .isLessThan(withheldBefore);
    }

    @Test
    @DisplayName("somebody who never declared is still taxed, under the default regime")
    void silence_is_not_an_exemption() throws Exception {
        Session owner = demoOwner();
        Session platform = platformOwner();
        setIncomeTax(platform, companyIdOf(platform, "Northwind Robotics"), true);

        // Sara has filled in nothing. Treating that as exempt would under-withhold from exactly the
        // people who did not get round to declaring, and the employer carries that liability.
        Session sara = login("sara.okoro@northwind.demo", PW);
        JsonNode line = lineNamed(myPayslip(sara), "Income tax (TDS)");
        JsonNode computation = getJson("/api/v1/tax/me/computation", sara);

        assertThat(computation.get("regime").asText()).isEqualTo("NEW");
        if (computation.get("totalTax").asLong() > 0) {
            assertThat(line).as("a taxable salary with no declaration is still taxed").isNotNull();
        } else {
            // On the new regime a modest salary is genuinely nil — that is the rebate, not a bug.
            assertThat(line).isNull();
        }
    }

    @Test
    @DisplayName("a payroll run does not cost a query per employee to work out tax")
    void the_run_batches_the_tax() throws Exception {
        Session owner = demoOwner();
        Session platform = platformOwner();
        setIncomeTax(platform, companyIdOf(platform, "Northwind Robotics"), true);

        // The run is the path that matters: a thousand people asking for their own declaration is
        // two thousand round trips to work out a deduction. This asserts the run still answers, and
        // QueryBudgetTest holds the statement count for payroll as a whole.
        JsonNode run = getJson("/api/v1/payroll/run?month=" + YearMonth.now().minusMonths(1), owner);
        assertThat(run.get("employees").asInt()).isPositive();
        assertThat(run.get("totalNet").asDouble()).isPositive();
    }
}
