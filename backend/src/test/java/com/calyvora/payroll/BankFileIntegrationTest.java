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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The bank file over HTTP.
 *
 * <p>The formatting and validation are proved in {@link BankFileBuilderTest}. What only a real
 * request shows: that the file actually carries the <em>unmasked</em> account number — the one thing
 * every other endpoint deliberately hides — and that a member cannot ask for it.
 */
class BankFileIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";
    private static final String ACCOUNT = "50100424268412";

    private Session addMember(Session owner, String email) throws Exception {
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", "MEMBER"))))
                .andExpect(status().isCreated());
        String token = email().lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "Emp", "lastName", "Loyee", "password", PW))))
                .andExpect(status().isOk());
        return login(email, PW);
    }

    /** An employee with a salary and, optionally, usable bank details. */
    private String employee(Session owner, int annual, boolean withBank) throws Exception {
        JsonNode people = getJson("/api/v1/people/employees", owner);
        String employeeId = people.get(0).get("id").asText();
        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/compensation")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("annualAmount", annual, "effectiveDate", "2026-01-01"))))
                .andExpect(status().isOk());
        if (withBank) {
            mockMvc.perform(patch("/api/v1/people/employees/" + employeeId + "/finance")
                            .header("Authorization", "Bearer " + owner.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("bankName", "HDFC Bank", "bankAccountNo", ACCOUNT,
                                    "bankIfsc", "HDFC0003939", "bankAccountName", "Ava R Chen"))))
                    .andExpect(status().isOk());
        }
        return employeeId;
    }

    @Test
    void the_file_carries_the_real_account_number_which_nothing_else_does() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = employee(owner, 1_200_000, true);

        // Every other endpoint masks it.
        JsonNode finance = getJson("/api/v1/people/employees/" + employeeId + "/finance", owner);
        assertThat(finance.get("bankAccountMasked").asText()).doesNotContain(ACCOUNT);

        MvcResult file = mockMvc.perform(get("/api/v1/payroll/bank-file?format=HDFC")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment; filename=")))
                .andReturn();

        String csv = file.getResponse().getContentAsString();
        assertThat(csv).contains(ACCOUNT).contains("HDFC0003939").contains("Ava R Chen");
        assertThat(csv).startsWith("Beneficiary Name,Beneficiary Account Number");
    }

    @Test
    void the_beneficiary_name_is_the_one_the_account_is_held_in() throws Exception {
        // "Ava R Chen" on the bank record, not the display name — the bank matches on the account but
        // a human reads the name on the statement, and a mismatch is what gets a transfer queried.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        employee(owner, 1_200_000, true);

        String csv = mockMvc.perform(get("/api/v1/payroll/bank-file")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("Ava R Chen");
    }

    @Test
    void the_preview_names_who_cannot_be_paid_and_never_shows_an_account_number() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        employee(owner, 1_200_000, false);   // salary, no bank details

        JsonNode preview = getJson("/api/v1/payroll/bank-file/preview", owner);

        assertThat(preview.get("payable").asInt()).isZero();
        assertThat(preview.get("excluded")).hasSize(1);
        assertThat(preview.get("excluded").get(0).get("reason").asText()).contains("account number");
        assertThat(preview.toString()).doesNotContain(ACCOUNT);
    }

    @Test
    void a_file_that_would_pay_nobody_is_refused_rather_than_downloaded_empty() throws Exception {
        // A header-only file looks downloadable and can still be uploaded.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        employee(owner, 1_200_000, false);

        mockMvc.perform(get("/api/v1/payroll/bank-file")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void a_member_cannot_download_the_company_bank_file() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        employee(owner, 1_200_000, true);
        Session member = addMember(owner, "member@acme.com");

        mockMvc.perform(get("/api/v1/payroll/bank-file")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/payroll/bank-file/preview")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void an_unsupported_bank_is_refused_by_name() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        employee(owner, 1_200_000, true);

        mockMvc.perform(get("/api/v1/payroll/bank-file?format=KOTAK")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest());
    }
}
