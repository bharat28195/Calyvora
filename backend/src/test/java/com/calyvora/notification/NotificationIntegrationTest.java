package com.calyvora.notification;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Inbox (feedback D4/D5): leave routes to the approver, decisions route back, goals notify the employee. */
class NotificationIntegrationTest extends IntegrationTestBase {

    private static final String DEMO_PW = "demopass123";

    @Test
    void a_leave_request_reaches_the_requesters_manager_and_not_everyone_else() throws Exception {
        seedDemo();
        // Priya (MEMBER) reports to Marcus (ADMIN) in the seeded org chart.
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        requestLeave(priya, "VACATION", "Family holiday");

        List<JsonNode> forMarcus = ofType(login("marcus.reed@northwind.demo", DEMO_PW), "LEAVE_REQUESTED");
        assertThat(forMarcus).hasSize(1);
        assertThat(forMarcus.get(0).get("title").asText()).contains("Priya Nair");
        assertThat(forMarcus.get(0).get("body").asText()).contains("Family holiday");
        assertThat(forMarcus.get(0).get("read").asBoolean()).isFalse();

        // The owner isn't the approver here, so no leave request lands in her inbox.
        assertThat(ofType(login("ava.chen@northwind.demo", DEMO_PW), "LEAVE_REQUESTED")).isEmpty();
    }

    @Test
    void with_no_manager_the_request_goes_to_every_admin() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");
        // The owner has no manager; the fallback is every Owner/Admin — but never yourself.
        requestLeave(owner, "SICK", null);
        assertThat(getJson("/api/v1/notifications", owner).size()).isZero();
    }

    @Test
    void the_decision_comes_back_to_the_requester() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        String leaveId = requestLeave(priya, "VACATION", null);

        Session marcus = login("marcus.reed@northwind.demo", DEMO_PW);
        mockMvc.perform(post("/api/v1/people/leave/" + leaveId + "/approve")
                .header("Authorization", bearer(marcus))).andExpect(status().isOk());

        List<JsonNode> decisions = ofType(priya, "LEAVE_APPROVED");
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).get("title").asText()).contains("approved");
    }

    @Test
    void a_goal_set_by_a_manager_notifies_the_employee_but_not_self_authored_ones() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        String priyaEmployeeId = employeeIdOf(owner, "priya.nair@northwind.demo");

        mockMvc.perform(post("/api/v1/people/employees/" + priyaEmployeeId + "/goals")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Lead the search rewrite"))))
                .andExpect(status().isCreated());

        List<JsonNode> goals = ofType(login("priya.nair@northwind.demo", DEMO_PW), "GOAL_ASSIGNED");
        assertThat(goals).hasSize(1);
        assertThat(goals.get(0).get("title").asText()).contains("Lead the search rewrite");

        // A goal you write for yourself shouldn't land in your own inbox.
        String ownerEmployeeId = employeeIdOf(owner, "ava.chen@northwind.demo");
        mockMvc.perform(post("/api/v1/people/employees/" + ownerEmployeeId + "/goals")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Raise the seed round"))))
                .andExpect(status().isCreated());
        assertThat(ofType(owner, "GOAL_ASSIGNED")).isEmpty();
    }

    @Test
    void unread_count_drops_as_things_are_read() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        requestLeave(priya, "VACATION", null);
        requestLeave(priya, "SICK", null);

        Session marcus = login("marcus.reed@northwind.demo", DEMO_PW);
        int start = getJson("/api/v1/notifications/unread-count", marcus).get("count").asInt();
        assertThat(start).isGreaterThanOrEqualTo(2);   // at least the two requests just made

        String first = getJson("/api/v1/notifications?unreadOnly=true", marcus).get(0).get("id").asText();
        mockMvc.perform(post("/api/v1/notifications/" + first + "/read").header("Authorization", bearer(marcus)))
                .andExpect(status().isOk());
        assertThat(getJson("/api/v1/notifications/unread-count", marcus).get("count").asInt()).isEqualTo(start - 1);

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", bearer(marcus)))
                .andExpect(status().isOk());
        assertThat(getJson("/api/v1/notifications/unread-count", marcus).get("count").asInt()).isZero();
    }

    @Test
    void an_inbox_is_private_to_its_owner() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        requestLeave(priya, "VACATION", null);

        Session marcus = login("marcus.reed@northwind.demo", DEMO_PW);
        String id = getJson("/api/v1/notifications", marcus).get(0).get("id").asText();

        // Someone else's notification id is a 404, not a peek.
        mockMvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", bearer(priya)))
                .andExpect(status().isNotFound());
    }

    // ---- helpers ----

    /**
     * The seeded demo generates its own notifications (expense claims), so tests assert on the
     * notifications of the type they're about rather than on inbox totals.
     */
    private List<JsonNode> ofType(Session s, String type) throws Exception {
        List<JsonNode> matching = new ArrayList<>();
        for (JsonNode n : getJson("/api/v1/notifications", s)) {
            if (type.equals(n.get("type").asText())) {
                matching.add(n);
            }
        }
        return matching;
    }

    private void seedDemo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private String requestLeave(Session s, String type, String reason) throws Exception {
        LocalDate start = LocalDate.now().plusDays(7);
        Map<String, String> body = reason == null
                ? Map.of("type", type, "startDate", start.toString(), "endDate", start.plusDays(2).toString())
                : Map.of("type", type, "startDate", start.toString(), "endDate", start.plusDays(2).toString(),
                        "reason", reason);
        MvcResult res = mockMvc.perform(post("/api/v1/people/leave").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private String employeeIdOf(Session admin, String email) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", admin)) {
            if (email.equals(e.get("email").asText())) {
                return e.get("id").asText();
            }
        }
        throw new AssertionError("no employee for " + email);
    }
}
