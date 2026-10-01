package com.calyvora.team;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who sees whose data, now that the answer is the reporting tree rather than the job title.
 *
 * <p>Three claims are being defended here, and each of them was false before this module:
 *
 * <ul>
 *   <li>A team belongs to whoever has reports, not to the MANAGER role. A MEMBER with an intern under
 *       them leads a team; a MANAGER with nobody under them does not.</li>
 *   <li>It reaches the whole subtree, not one level. A skip-level lead saw nothing of their leads'
 *       reports, which is most of their org.</li>
 *   <li>It stops at the edge of that subtree. This is the one that matters: opening a team endpoint
 *       without scoping is a wider hole than the gap it closes, and would look perfectly correct in a
 *       demo company with a single team.</li>
 * </ul>
 */
class OrgScopeTest extends IntegrationTestBase {

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

    private String employeeIdOf(Session admin, String email) throws Exception {
        for (JsonNode p : getJson("/api/v1/people/employees", admin)) {
            if (email.equalsIgnoreCase(p.path("email").asText())) {
                return p.get("id").asText();
            }
        }
        throw new AssertionError("no employee found for " + email);
    }

    private void setManager(Session admin, String employeeId, String managerEmployeeId) throws Exception {
        mockMvc.perform(patch("/api/v1/people/employees/" + employeeId)
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("managerId", managerEmployeeId))))
                .andExpect(status().isOk());
    }

    /**
     * head → lead → intern, plus an unrelated MANAGER with nobody under them.
     *
     * <p>The head and the lead are both MEMBERs on purpose. If any of this passed because of a role
     * rather than the tree, making the leads MEMBERs is what exposes it.
     */
    private record Org(Session owner, Session head, Session lead, Session intern, Session loneManager) {}

    private Org threeDeep() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session head = addUser(owner, "head@acme.com", "MEMBER", "Hana");
        Session lead = addUser(owner, "lead@acme.com", "MEMBER", "Leo");
        Session intern = addUser(owner, "intern@acme.com", "MEMBER", "Ira");
        Session loneManager = addUser(owner, "lonely@acme.com", "MANAGER", "Lone");

        setManager(owner, employeeIdOf(owner, "lead@acme.com"), employeeIdOf(owner, "head@acme.com"));
        setManager(owner, employeeIdOf(owner, "intern@acme.com"), employeeIdOf(owner, "lead@acme.com"));
        return new Org(owner, head, lead, intern, loneManager);
    }

    @Test
    void a_member_with_reports_leads_a_team() throws Exception {
        Org org = threeDeep();

        JsonNode standing = getJson("/api/v1/team/mine", org.lead());
        assertThat(standing.get("leadsTeam").asBoolean()).isTrue();
        assertThat(standing.get("directCount").asInt()).isEqualTo(1);
    }

    @Test
    void a_manager_with_no_reports_leads_nobody() throws Exception {
        Org org = threeDeep();

        JsonNode standing = getJson("/api/v1/team/mine", org.loneManager());
        assertThat(standing.get("leadsTeam").asBoolean())
                .as("the MANAGER role must not conjure a team out of nothing")
                .isFalse();
        assertThat(standing.get("totalCount").asInt()).isZero();

        assertThat(getJson("/api/v1/team", org.loneManager()).get("members")).isEmpty();
    }

    @Test
    void the_downline_reaches_past_the_first_level() throws Exception {
        Org org = threeDeep();

        JsonNode standing = getJson("/api/v1/team/mine", org.head());
        assertThat(standing.get("directCount").asInt()).isEqualTo(1);
        assertThat(standing.get("totalCount").asInt())
                .as("the head leads the lead AND the lead's intern")
                .isEqualTo(2);

        JsonNode everyone = getJson("/api/v1/team", org.head());
        assertThat(everyone.get("members")).hasSize(2);

        JsonNode directOnly = getJson("/api/v1/team?direct=true", org.head());
        assertThat(directOnly.get("members")).hasSize(1);
        assertThat(directOnly.get("members").get(0).get("name").asText()).isEqualTo("Leo Person");
    }

    @Test
    void the_downline_does_not_reach_upward_or_sideways() throws Exception {
        Org org = threeDeep();

        // The intern is nobody's manager.
        assertThat(getJson("/api/v1/team", org.intern()).get("members")).isEmpty();

        // The lead sees the intern below them and NOT the head above them.
        JsonNode leadsTeam = getJson("/api/v1/team", org.lead());
        assertThat(leadsTeam.get("members")).hasSize(1);
        assertThat(leadsTeam.get("members").get(0).get("name").asText()).isEqualTo("Ira Person");
    }

    @Test
    void one_persons_month_is_refused_to_somebody_outside_their_chain() throws Exception {
        Org org = threeDeep();
        String internEmployeeId = employeeIdOf(org.owner(), "intern@acme.com");

        // The head is two levels up and may look.
        mockMvc.perform(get("/api/v1/team/attendance/" + internEmployeeId)
                        .header("Authorization", "Bearer " + org.head().accessToken()))
                .andExpect(status().isOk());

        // The unrelated manager may not — and is told so, rather than handed an empty month that
        // would read as "this person never came to work".
        mockMvc.perform(get("/api/v1/team/attendance/" + internEmployeeId)
                        .header("Authorization", "Bearer " + org.loneManager().accessToken()))
                .andExpect(status().isForbidden());
    }

    /**
     * A reporting loop would make two people each other's subordinate and hand each of them the
     * other's attendance, leave and reviews — a privilege escalation two profile edits deep, available
     * to anybody who can edit an org chart. Only self-management was blocked before.
     */
    @Test
    void a_reporting_loop_is_refused() throws Exception {
        Org org = threeDeep();
        String headId = employeeIdOf(org.owner(), "head@acme.com");
        String internId = employeeIdOf(org.owner(), "intern@acme.com");

        // The intern already sits under the head, so making the intern the head's manager is a loop.
        mockMvc.perform(patch("/api/v1/people/employees/" + headId)
                        .header("Authorization", "Bearer " + org.owner().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("managerId", internId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hr_still_sees_the_whole_company_and_a_lead_does_not() throws Exception {
        Org org = threeDeep();
        Session hr = addUser(org.owner(), "hr@acme.com", "HR", "Hedy");

        // Exits was the leak: it listed every resignation in the business to anybody who could open
        // the screen, which included every manager.
        assertThat(getJson("/api/v1/people/exits", hr)).isEmpty();
        assertThat(getJson("/api/v1/people/exits", org.lead())).isEmpty();

        mockMvc.perform(post("/api/v1/people/employees/" + employeeIdOf(org.owner(), "lonely@acme.com") + "/exit")
                        .header("Authorization", "Bearer " + org.owner().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lastWorkingDay",
                                java.time.LocalDate.now().plusMonths(1).toString(), "reason", "moving on"))))
                .andExpect(status().isOk());

        assertThat(getJson("/api/v1/people/exits", hr))
                .as("HR does people-ops for the whole company")
                .hasSize(1);
        assertThat(getJson("/api/v1/people/exits", org.lead()))
                .as("the departing person is not in this lead's org")
                .isEmpty();
    }
}
