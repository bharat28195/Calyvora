package com.calyvora.billing;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Payslip template (generation + validation) and per-employee subscription billing. */
class PayrollBillingIntegrationTest extends IntegrationTestBase {

    private static final String DEMO_PW = "demopass123";

    @Test
    void the_default_template_drives_payslip_generation() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);

        // Default template = 5 components (Basic, HRA, Special, PF, Income tax).
        assertThat(getJson("/api/v1/payroll/payslip-template", owner).size()).isEqualTo(5);

        // Priya earns 145000/yr → 12083.33/mo. Net should be gross minus PF(12% of basic)+tax(10% of gross).
        String priyaEmp = firstEmployeeNamed(owner, "Priya Nair");
        JsonNode slip = getJson("/api/v1/people/employees/" + priyaEmp + "/payslip", owner);
        assertThat(slip.get("gross").asDouble()).isEqualTo(12083.33);
        assertThat(slip.get("net").asDouble()).isEqualTo(10150.00);
        assertThat(slip.get("earnings").size()).isEqualTo(3);
        assertThat(slip.get("deductions").size()).isEqualTo(2);
    }

    @Test
    void a_template_with_over_100_percent_earnings_is_rejected() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        var bad = Map.of("components", List.of(
                Map.of("name", "Basic", "kind", "EARNING", "calc", "PERCENT_OF_GROSS", "value", 80, "basis", true),
                Map.of("name", "Bonus", "kind", "EARNING", "calc", "PERCENT_OF_GROSS", "value", 40, "basis", false)));
        mockMvc.perform(put("/api/v1/payroll/payslip-template").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void billing_charges_per_active_employee_per_month() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);

        JsonNode b = getJson("/api/v1/billing", owner);
        // Northwind is on the published price list, well inside the first tier (₹149 up to 100).
        assertThat(b.get("pricePerEmployee").asDouble()).isEqualTo(149.0);
        assertThat(b.get("pricePerEmployeePerYear").asDouble()).isEqualTo(1788.0);
        assertThat(b.get("billableEmployees").asInt()).isEqualTo(6);
        assertThat(b.get("monthlyCharge").asDouble()).isEqualTo(894.0);   // 6 × 149
        assertThat(b.get("annualCharge").asDouble()).isEqualTo(10728.0);
        // The tier ladder is sent so the UI can explain a bill that isn't headcount × one rate.
        assertThat(b.get("tiers")).hasSize(2);
        // The demo seeds Northwind with a live (ACTIVE) subscription so it shows up real in the
        // platform owner console (PD-10).
        assertThat(b.get("status").asText()).isEqualTo("ACTIVE");

        // Activate, then pay the current month → it reads back as paid.
        mockMvc.perform(post("/api/v1/billing/activate").header("Authorization", bearer(owner))).andExpect(status().isOk());
        String month = getJson("/api/v1/billing", owner).get("currentMonth").asText();
        JsonNode paid = objectMapper.readTree(mockMvc.perform(post("/api/v1/billing/invoices/" + month + "/pay")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(paid.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(paid.get("paidThrough").asText()).isEqualTo(month);
    }

    @Test
    void billing_and_template_are_admin_only() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        mockMvc.perform(get("/api/v1/billing").header("Authorization", bearer(priya))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/payroll/payslip-template").header("Authorization", bearer(priya))).andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private void seedDemo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private String firstEmployeeNamed(Session s, String name) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", s)) {
            if (name.equals((e.get("firstName").asText() + " " + e.get("lastName").asText()))) {
                return e.get("id").asText();
            }
        }
        throw new AssertionError("no employee " + name);
    }
}
