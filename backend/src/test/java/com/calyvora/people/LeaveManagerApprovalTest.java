package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Managers decide their own team's leave — and <em>only</em> their own team's.
 *
 * <p>The gap this closes: listing and deciding leave were restricted to OWNER/ADMIN/HR, so a manager
 * could see their team but not action a single request, and every holiday in the company funnelled
 * through HR. The product contradicted itself, too — attendance regularizations were already scoped to
 * the caller's reports, so the same manager could approve a missed punch but not a day off.
 *
 * <p>The half that matters more is the second one. Opening the endpoint to MANAGER without scoping
 * would let <em>any</em> manager approve <em>any</em> employee's leave anywhere in the company: a
 * wider hole than the one being fixed, and one that would look perfectly fine in a demo with a single
 * team. {@code managerB_cannot_*} are the tests that would catch it.
 */
class LeaveManagerApprovalTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private Session addUser(Session owner, String email, String role, String firstName) throws Exception {
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", role))))
                .andExpect(status().isCreated());
        String token = email().lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", firstName,
                                "lastName", "Person", "password", PW))))
                .andExpect(status().isOk());
        return login(email, PW);
    }

    /** The employee id of the person with this email, from the directory. */
    private String employeeIdOf(Session admin, String email) throws Exception {
        JsonNode people = getJson("/api/v1/people/employees", admin);
        for (JsonNode p : people) {
            if (email.equalsIgnoreCase(p.path("email").asText())) {
                return p.get("id").asText();
            }
        }
        throw new AssertionError("no employee found for " + email + " in " + people);
    }

    private void setManager(Session admin, String employeeId, String managerEmployeeId) throws Exception {
        mockMvc.perform(patch("/api/v1/people/employees/" + employeeId)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("managerId", managerEmployeeId))))
                .andExpect(status().isOk());
    }

    private String requestLeave(Session s, String start, String end) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/people/leave")
                        .header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "VACATION", "startDate", start,
                                "endDate", end, "reason", "time off"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    /** Two managers, one report each, and a leave request from each report. */
    private record Fixture(Session owner, Session managerA, Session managerB,
                           Session reportA, Session reportB, String leaveA, String leaveB) {}

    private Fixture twoTeams() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session managerA = addUser(owner, "mgr.a@acme.com", "MANAGER", "Mala");
        Session managerB = addUser(owner, "mgr.b@acme.com", "MANAGER", "Bala");
        Session reportA = addUser(owner, "rep.a@acme.com", "MEMBER", "Arun");
        Session reportB = addUser(owner, "rep.b@acme.com", "MEMBER", "Bina");

        setManager(owner, employeeIdOf(owner, "rep.a@acme.com"), employeeIdOf(owner, "mgr.a@acme.com"));
        setManager(owner, employeeIdOf(owner, "rep.b@acme.com"), employeeIdOf(owner, "mgr.b@acme.com"));

        String year = String.valueOf(LocalDate.now().getYear());
        String leaveA = requestLeave(reportA, year + "-08-03", year + "-08-04");
        String leaveB = requestLeave(reportB, year + "-09-07", year + "-09-08");
        return new Fixture(owner, managerA, managerB, reportA, reportB, leaveA, leaveB);
    }

    @Test
    void manager_sees_only_their_own_reports_requests() throws Exception {
        Fixture f = twoTeams();

        JsonNode inboxA = getJson("/api/v1/people/leave", f.managerA());
        assertThat(inboxA).hasSize(1);
        assertThat(inboxA.get(0).get("id").asText()).isEqualTo(f.leaveA());

        JsonNode inboxB = getJson("/api/v1/people/leave", f.managerB());
        assertThat(inboxB).hasSize(1);
        assertThat(inboxB.get(0).get("id").asText()).isEqualTo(f.leaveB());
    }

    @Test
    void manager_approves_their_own_report() throws Exception {
        Fixture f = twoTeams();

        mockMvc.perform(post("/api/v1/people/leave/" + f.leaveA() + "/approve")
                        .header("Authorization", "Bearer " + f.managerA().accessToken()))
                .andExpect(status().isOk());

        JsonNode mine = getJson("/api/v1/people/leave/mine", f.reportA());
        assertThat(mine.get(0).get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void managerB_cannot_approve_managerAs_report() throws Exception {
        Fixture f = twoTeams();

        mockMvc.perform(post("/api/v1/people/leave/" + f.leaveA() + "/approve")
                        .header("Authorization", "Bearer " + f.managerB().accessToken()))
                .andExpect(status().isForbidden());

        // And the request is untouched, not merely un-approved for this caller.
        JsonNode mine = getJson("/api/v1/people/leave/mine", f.reportA());
        assertThat(mine.get(0).get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void managerB_cannot_reject_managerAs_report() throws Exception {
        Fixture f = twoTeams();

        mockMvc.perform(post("/api/v1/people/leave/" + f.leaveA() + "/reject")
                        .header("Authorization", "Bearer " + f.managerB().accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void hr_and_admin_still_see_and_decide_everything() throws Exception {
        Fixture f = twoTeams();
        Session hr = addUser(f.owner(), "hr@acme.com", "HR", "Hema");

        JsonNode inbox = getJson("/api/v1/people/leave", hr);
        assertThat(inbox).hasSize(2);

        // HR has no reports of their own; scoping by reports would give them an empty inbox, which is
        // the obvious way to get this change wrong.
        mockMvc.perform(post("/api/v1/people/leave/" + f.leaveA() + "/approve")
                        .header("Authorization", "Bearer " + hr.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/people/leave/" + f.leaveB() + "/reject")
                        .header("Authorization", "Bearer " + hr.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void a_manager_with_no_reports_gets_an_empty_inbox_not_everyones() throws Exception {
        Fixture f = twoTeams();
        Session lone = addUser(f.owner(), "mgr.c@acme.com", "MANAGER", "Chitra");

        assertThat(getJson("/api/v1/people/leave", lone)).isEmpty();
    }

    @Test
    void a_plain_member_still_cannot_reach_the_approvals_inbox() throws Exception {
        Fixture f = twoTeams();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/people/leave")
                        .header("Authorization", "Bearer " + f.reportA().accessToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/people/leave/" + f.leaveB() + "/approve")
                        .header("Authorization", "Bearer " + f.reportA().accessToken()))
                .andExpect(status().isForbidden());
    }
}
