package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Money on a payslip is denominated in the currency the company chose in settings. It used to be read
 * off the employee's salary row, which defaulted to USD — so an INR company issued payslips reading
 * "All amounts are in USD" and an amount in words ending "USD Only".
 */
class PayslipCurrencyTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Test
    void the_payslip_is_denominated_in_the_companys_configured_currency() throws Exception {
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);
        setCurrency(ava, "INR");

        mockMvc.perform(get("/api/v1/people/me/payslip?month=2026-03")
                        .header("Authorization", "Bearer " + ava.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.netInWords").value(
                        org.hamcrest.Matchers.containsString("Rupees")));
    }

    @Test
    void changing_the_company_currency_changes_what_the_payslip_says() throws Exception {
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);
        setCurrency(ava, "USD");

        mockMvc.perform(get("/api/v1/people/me/payslip?month=2026-03")
                        .header("Authorization", "Bearer " + ava.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void the_payroll_run_reports_the_same_currency_as_the_payslips_it_totals() throws Exception {
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);
        setCurrency(ava, "INR");

        mockMvc.perform(get("/api/v1/payroll/run?month=2026-03")
                        .header("Authorization", "Bearer " + ava.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void saving_settings_without_the_identity_fields_does_not_erase_them() throws Exception {
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);

        Map<String, Object> full = new LinkedHashMap<>();
        full.put("timezone", "Asia/Kolkata");
        full.put("locale", "en");
        full.put("currency", "INR");
        full.put("legalName", "Northwind Robotics Private Limited");
        full.put("address", "Thaltej, Ahmedabad");
        mockMvc.perform(patch("/api/v1/company/settings")
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(full)))
                .andExpect(status().isOk());

        // A client saving only the localisation half must not wipe the legal name and address, which
        // are printed on every payslip and are edited from a different part of the UI.
        mockMvc.perform(patch("/api/v1/company/settings")
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("timezone", "Asia/Kolkata", "locale", "en", "currency", "INR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Northwind Robotics Private Limited"))
                .andExpect(jsonPath("$.address").value("Thaltej, Ahmedabad"));
    }

    private void setCurrency(Session session, String currency) throws Exception {
        mockMvc.perform(patch("/api/v1/company/settings")
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("timezone", "Asia/Kolkata", "locale", "en", "currency", currency))))
                .andExpect(status().isOk());
    }

    private void seed() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }
}
