package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "My Finances" holds the most sensitive data in the product — bank account and PAN. Three rules
 * have to hold, and each is a different way of getting it wrong:
 *
 * <ol>
 *   <li>a colleague can never read it, not even a manager;</li>
 *   <li>the full account number and PAN never leave the server, even for the rightful owner;</li>
 *   <li>an employee can't edit their own PF/ESI enrolment — those are employer filings.</li>
 * </ol>
 */
class EmployeeFinanceIntegrationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Test
    void an_employee_sees_their_own_record_with_the_account_and_pan_masked() throws Exception {
        seed();
        Session sara = login("sara.okoro@northwind.demo", PW);

        JsonNode finance = getJson("/api/v1/people/me/finance", sara);

        assertThat(finance.get("bankName").asText()).isEqualTo("State Bank of India");
        // Masked, not merely hidden from the UI — the raw value must not travel at all.
        assertThat(finance.get("bankAccountMasked").asText()).endsWith("5566").startsWith("XXX");
        assertThat(finance.get("bankAccountMasked").asText()).doesNotContain("38240015566");
        assertThat(finance.get("panMasked").asText()).isEqualTo("XXXXXX456D");
    }

    @Test
    void an_employee_cannot_read_a_colleagues_finance_record() throws Exception {
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);
        Session sara = login("sara.okoro@northwind.demo", PW);
        String avaEmployeeId = getJson("/api/v1/people/me", ava).get("id").asText();

        // The HR-facing endpoint is role-gated; a member has no route to anyone else's bank details.
        mockMvc.perform(get("/api/v1/people/employees/" + avaEmployeeId + "/finance")
                        .header("Authorization", "Bearer " + sara.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void an_employee_can_update_their_own_bank_details() throws Exception {
        seed();
        Session sara = login("sara.okoro@northwind.demo", PW);

        mockMvc.perform(patch("/api/v1/people/me/finance")
                        .header("Authorization", "Bearer " + sara.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bankName", "Kotak Mahindra Bank",
                                "bankAccountNo", "778899001122"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("Kotak Mahindra Bank"))
                .andExpect(jsonPath("$.bankAccountMasked").value("XXXXXXXX1122"));
    }

    @Test
    void an_employee_cannot_change_their_own_pf_enrolment() throws Exception {
        // PF/ESI drive employer filings. Self-service here would be a compliance hole, not a feature.
        seed();
        Session sara = login("sara.okoro@northwind.demo", PW);

        mockMvc.perform(patch("/api/v1/people/me/finance")
                        .header("Authorization", "Bearer " + sara.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pfStatus", "ENABLED", "uan", "999999999999"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void hr_can_maintain_anyones_statutory_details() throws Exception {
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);   // ADMIN
        String saraId = employeeIdByEmail(ava, "sara.okoro@northwind.demo");

        mockMvc.perform(patch("/api/v1/people/employees/" + saraId + "/finance")
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pfStatus", "ENABLED", "uan", "101794989999"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pfStatus").value("ENABLED"))
                .andExpect(jsonPath("$.uan").value("101794989999"));
    }

    @Test
    void changing_a_pan_clears_its_verified_flag() throws Exception {
        // Otherwise the green "verified" tick would carry over to a document nobody has checked.
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);
        String saraId = employeeIdByEmail(ava, "sara.okoro@northwind.demo");
        assertThat(getJson("/api/v1/people/employees/" + saraId + "/finance", ava)
                .get("panVerified").asBoolean()).isTrue();

        mockMvc.perform(patch("/api/v1/people/employees/" + saraId + "/finance")
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("panNumber", "ZZZPO1111Z"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panVerified").value(false));
    }

    @Test
    void a_malformed_pan_or_uan_is_rejected() throws Exception {
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);
        String saraId = employeeIdByEmail(ava, "sara.okoro@northwind.demo");

        mockMvc.perform(patch("/api/v1/people/employees/" + saraId + "/finance")
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("panNumber", "NOT-A-PAN"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/people/employees/" + saraId + "/finance")
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("uan", "123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void the_payslip_carries_the_company_branding_and_statutory_identifiers() throws Exception {
        // The point of the whole feature: a payslip that reads as a document, not a stub.
        seed();
        Session sara = login("sara.okoro@northwind.demo", PW);

        JsonNode slip = getJson("/api/v1/people/me/payslip", sara);

        assertThat(slip.get("companyName").asText()).isEqualTo("Northwind Robotics Private Limited");
        assertThat(slip.get("companyAddress").asText()).contains("Ahmedabad");
        assertThat(slip.get("employeeNo").asText()).isEqualTo("NR-005");
        assertThat(slip.get("designation").asText()).isEqualTo("Support Specialist");
        assertThat(slip.get("department").asText()).isEqualTo("Customer Support");
        assertThat(slip.get("panMasked").asText()).isEqualTo("XXXXXX456D");
        assertThat(slip.get("netInWords").asText()).endsWith("Only");
    }

    private void seed() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private String employeeIdByEmail(Session session, String email) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", session)) {
            if (e.get("email").asText().equals(email)) {
                return e.get("id").asText();
            }
        }
        throw new AssertionError("employee not found: " + email);
    }
}
