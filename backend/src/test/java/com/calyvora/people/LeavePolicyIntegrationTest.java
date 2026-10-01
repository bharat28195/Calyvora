package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Leave policy and comp-off, end to end.
 *
 * <p>The accrual arithmetic itself is proved in {@link LeaveEntitlementTest}; this covers the things
 * only a real request can: that a company's policy is what the balance uses, that editing it is
 * restricted, and that a comp-off credit is earned once, spent once, and cannot be spent twice.
 */
class LeavePolicyIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private Session addUser(Session owner, String email, String role) throws Exception {
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", role))))
                .andExpect(status().isCreated());
        String token = email().lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "Test",
                                "lastName", "Person", "password", PW))))
                .andExpect(status().isOk());
        return login(email, PW);
    }

    private JsonNode balanceFor(Session s, String type) throws Exception {
        JsonNode all = getJson("/api/v1/people/leave/balances", s);
        for (JsonNode b : all) {
            if (type.equals(b.get("type").asText())) {
                return b;
            }
        }
        throw new AssertionError("no balance for " + type + " in " + all);
    }

    private String claimCompOff(Session s, String workedOn) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/people/comp-off")
                        .header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("workedOn", workedOn, "reason", "release night"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    // ---- policy ----

    @Test
    void a_new_company_starts_on_the_old_hard_coded_allowance() throws Exception {
        // The migration's whole promise: nothing changes for anybody on the day policies arrive.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);

        JsonNode policies = getJson("/api/v1/people/leave-policies", owner);
        assertThat(policies).hasSize(LeaveType.values().length);

        mockMvc.perform(get("/api/v1/people/leave/balance")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowanceDays").value(25));
    }

    @Test
    void changing_the_policy_changes_the_balance() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);

        mockMvc.perform(patch("/api/v1/people/leave-policies/VACATION")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("daysPerYear", 18, "carryForwardCap", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysPerYear").value(18.0))
                .andExpect(jsonPath("$.carryForwardCap").value(5.0));

        mockMvc.perform(get("/api/v1/people/leave/balance")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowanceDays").value(18));
    }

    @Test
    void a_member_can_read_the_policy_but_not_change_it() throws Exception {
        // Reading is deliberate: an employee is entitled to know how their own leave is calculated.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session member = addUser(owner, "member@acme.com", "MEMBER");

        assertThat(getJson("/api/v1/people/leave-policies", member)).isNotEmpty();

        mockMvc.perform(patch("/api/v1/people/leave-policies/VACATION")
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("daysPerYear", 99))))
                .andExpect(status().isForbidden());
    }

    @Test
    void a_carry_forward_cap_above_the_entitlement_is_refused() throws Exception {
        // Not an error in arithmetic, but always a mistake: a year cannot produce more than it grants,
        // so the number would silently never apply.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);

        mockMvc.perform(patch("/api/v1/people/leave-policies/VACATION")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("daysPerYear", 10, "carryForwardCap", 30))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pending_requests_are_held_back_from_the_available_balance() throws Exception {
        // Otherwise two requests made before either is decided both look affordable, and the second is
        // refused at approval time by an approver who has no idea why.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session member = addUser(owner, "member@acme.com", "MEMBER");
        String year = String.valueOf(LocalDate.now().getYear());

        mockMvc.perform(post("/api/v1/people/leave")
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "VACATION", "startDate", year + "-06-01",
                                "endDate", year + "-06-05", "reason", "holiday"))))
                .andExpect(status().isCreated());

        JsonNode vacation = balanceFor(member, "VACATION");
        assertThat(vacation.get("pendingDays").decimalValue().intValue()).isEqualTo(5);
        assertThat(vacation.get("availableDays").decimalValue().intValue()).isEqualTo(20);
    }

    // ---- comp-off ----

    @Test
    void comp_off_is_claimed_approved_and_spent_once() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session member = addUser(owner, "member@acme.com", "MEMBER");
        String year = String.valueOf(LocalDate.now().getYear());
        String worked = LocalDate.now().minusDays(10).toString();

        // Nothing is available until a claim is approved.
        assertThat(balanceFor(member, "COMP_OFF").get("availableDays").decimalValue().intValue()).isZero();

        String creditId = claimCompOff(member, worked);
        assertThat(balanceFor(member, "COMP_OFF").get("availableDays").decimalValue().intValue())
                .as("a pending claim is not yet a day off").isZero();

        assertThat(getJson("/api/v1/people/comp-off/pending", owner)).hasSize(1);
        mockMvc.perform(post("/api/v1/people/comp-off/" + creditId + "/approve")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.spendable").value(true));

        assertThat(balanceFor(member, "COMP_OFF").get("availableDays").decimalValue().intValue()).isEqualTo(1);

        // Spend it.
        MvcResult leave = mockMvc.perform(post("/api/v1/people/leave")
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "COMP_OFF", "startDate", year + "-11-02",
                                "endDate", year + "-11-02", "reason", "day off"))))
                .andExpect(status().isCreated())
                .andReturn();
        String leaveId = objectMapper.readTree(leave.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/people/leave/" + leaveId + "/approve")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());

        // And it is gone — a credit is single-use.
        assertThat(balanceFor(member, "COMP_OFF").get("availableDays").decimalValue().intValue()).isZero();
    }

    @Test
    void comp_off_leave_cannot_be_requested_without_credits() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session member = addUser(owner, "member@acme.com", "MEMBER");
        String year = String.valueOf(LocalDate.now().getYear());

        mockMvc.perform(post("/api/v1/people/leave")
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "COMP_OFF", "startDate", year + "-11-02",
                                "endDate", year + "-11-02", "reason", "day off"))))
                .andExpect(status().isConflict());
    }

    @Test
    void the_same_day_cannot_be_claimed_twice() throws Exception {
        // Without this, asking for the same Saturday twice is two days off.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session member = addUser(owner, "member@acme.com", "MEMBER");
        String worked = LocalDate.now().minusDays(3).toString();

        claimCompOff(member, worked);
        mockMvc.perform(post("/api/v1/people/comp-off")
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("workedOn", worked))))
                .andExpect(status().isConflict());
    }

    @Test
    void a_comp_off_cannot_be_claimed_for_a_day_not_yet_worked() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session member = addUser(owner, "member@acme.com", "MEMBER");

        mockMvc.perform(post("/api/v1/people/comp-off")
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("workedOn", LocalDate.now().plusDays(7).toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void a_member_cannot_approve_their_own_comp_off() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session member = addUser(owner, "member@acme.com", "MEMBER");
        String creditId = claimCompOff(member, LocalDate.now().minusDays(2).toString());

        mockMvc.perform(post("/api/v1/people/comp-off/" + creditId + "/approve")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isForbidden());
    }
}
