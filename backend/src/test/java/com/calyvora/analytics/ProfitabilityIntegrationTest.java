package com.calyvora.analytics;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Project profitability (PD-22, D2) — payroll cost allocated across the work it produced, against
 * what that work was sold for.
 *
 * <p>The assertions are deliberately about <em>ratios and identities</em> rather than absolute
 * rupees: the day-count depends on when the suite runs, but "cost splits 3:1 by points", "the parts
 * sum to the whole", and "margin is fee minus cost" hold on every calendar.
 */
class ProfitabilityIntegrationTest extends IntegrationTestBase {

    private static final String PW = "OwnerPass123!";

    @Test
    void cost_follows_delivered_work_and_margin_follows_the_agreed_fee() throws Exception {
        Session owner = onboardOwner("Delivery Co", "owner@delivery.test", PW);
        String me = employeeId(owner);
        setSalary(owner, me, 1_000_000);

        String clientId = createClient(owner, "Northwind Ltd");
        String billableProject = createProject(owner, "Client Build", "CB");
        String internalProject = createProject(owner, "Internal Tooling", "IT");

        // The billable one is sold for a fixed fee; the internal one is ours.
        patchJson("/api/v1/work/projects/" + billableProject, owner, Map.of(
                "clientId", clientId, "billable", true, "contractValue", 400000));

        // 3 points delivered on the client build, 1 on internal → cost should split 3:1.
        finish(owner, createTask(owner, billableProject, "Ship the integration", me, 3));
        finish(owner, createTask(owner, internalProject, "Tidy the build script", me, 1));

        JsonNode p = profitability(owner);
        assertThat(p.get("currency").asText()).isNotBlank();
        assertThat(p.get("basis").asText()).contains("allocated, not measured");

        JsonNode client = projectLine(p, "CB");
        JsonNode internal = projectLine(p, "IT");
        double clientCost = client.get("cost").asDouble();
        double internalCost = internal.get("cost").asDouble();

        assertThat(clientCost).isGreaterThan(0);
        assertThat(clientCost / internalCost).isCloseTo(3.0, org.assertj.core.data.Offset.offset(0.02));

        // The allocation is a split of one person's pay, so the parts must sum to the whole.
        assertThat(clientCost + internalCost)
                .isCloseTo(p.get("totalCost").asDouble(), org.assertj.core.data.Offset.offset(0.02));

        // Margin is the agreed fee less the engagement's whole cost — never one period's slice, or a
        // year-long contract would show a ~100% margin in its first month. Here the project is new,
        // so cost-to-date and period cost coincide.
        assertThat(client.get("contractValue").asDouble()).isEqualTo(400000.0);
        assertThat(client.get("costToDate").asDouble()).isCloseTo(clientCost, org.assertj.core.data.Offset.offset(0.02));
        assertThat(client.get("margin").asDouble())
                .isCloseTo(400000.0 - client.get("costToDate").asDouble(), org.assertj.core.data.Offset.offset(0.02));
        assertThat(client.get("clientName").asText()).isEqualTo("Northwind Ltd");
        assertThat(client.get("billable").asBoolean()).isTrue();
        assertThat(internal.get("margin").isNull()).isTrue();
        assertThat(internal.get("billable").asBoolean()).isFalse();

        // Three quarters of this person's effort went to sold work.
        JsonNode person = p.get("people").get(0);
        assertThat(person.get("billablePercent").asDouble()).isCloseTo(75.0, org.assertj.core.data.Offset.offset(0.2));
        assertThat(person.get("pointsDelivered").asInt()).isEqualTo(4);
        assertThat(person.get("projectsTouched").asInt()).isEqualTo(2);
    }

    @Test
    void payroll_with_nothing_delivered_is_reported_not_hidden() throws Exception {
        Session owner = onboardOwner("Idle Co", "owner@idle.test", PW);
        String me = employeeId(owner);
        setSalary(owner, me, 600_000);

        // Paid, but nothing closed in the period. Spreading this over projects would make every one
        // of them look cheaper than it was, so it has to surface on its own.
        JsonNode p = profitability(owner);
        assertThat(p.get("projects")).isEmpty();
        assertThat(p.get("unallocatedCost").asDouble()).isGreaterThan(0);
        assertThat(p.get("totalCost").asDouble()).isEqualTo(p.get("unallocatedCost").asDouble());
        assertThat(p.get("people").get(0).get("projectsTouched").asInt()).isZero();
    }

    @Test
    void unestimated_work_still_allocates_by_item_count() throws Exception {
        Session owner = onboardOwner("Nopoints Co", "owner@nopoints.test", PW);
        String me = employeeId(owner);
        setSalary(owner, me, 1_200_000);

        String a = createProject(owner, "Alpha", "AL");
        String b = createProject(owner, "Beta", "BE");
        finish(owner, createTask(owner, a, "One", me, null));
        finish(owner, createTask(owner, a, "Two", me, null));
        finish(owner, createTask(owner, b, "Three", me, null));

        // No estimates anywhere, so weight falls back to how many items were closed: 2 versus 1.
        JsonNode p = profitability(owner);
        double alpha = projectLine(p, "AL").get("cost").asDouble();
        double beta = projectLine(p, "BE").get("cost").asDouble();
        assertThat(alpha / beta).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.02));
        assertThat(p.get("unallocatedCost").asDouble()).isZero();
    }

    @Test
    void the_numbers_are_salaries_so_only_people_who_may_see_pay_may_see_them() throws Exception {
        Session owner = onboardOwner("Private Co", "owner@private.test", PW);
        Session member = addMember(owner, "member@private.test");

        mockMvc.perform(get("/api/v1/analytics/profitability").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analytics/profitability").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void one_tenants_costs_never_appear_in_anothers() throws Exception {
        Session a = onboardOwner("Tenant A", "owner@tenant-a.test", PW);
        String aEmp = employeeId(a);
        setSalary(a, aEmp, 900_000);
        String project = createProject(a, "A Work", "AW");
        finish(a, createTask(a, project, "A task", aEmp, 5));

        Session b = onboardOwner("Tenant B", "owner@tenant-b.test", PW);
        JsonNode theirs = profitability(b);
        assertThat(theirs.get("projects")).isEmpty();
        assertThat(theirs.get("totalCost").asDouble()).isZero();
    }

    @Test
    void a_backwards_period_is_rejected() throws Exception {
        Session owner = onboardOwner("Dates Co", "owner@dates.test", PW);
        mockMvc.perform(get("/api/v1/analytics/profitability?from=2026-12-31&to=2026-01-01")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    /** The current month, which is what the endpoint defaults to and where the tasks land. */
    private JsonNode profitability(Session s) throws Exception {
        YearMonth m = YearMonth.now();
        LocalDate from = m.atDay(1);
        LocalDate to = m.atEndOfMonth();
        return getJson("/api/v1/analytics/profitability?from=" + from + "&to=" + to, s);
    }

    private JsonNode projectLine(JsonNode profitability, String key) {
        for (JsonNode line : profitability.get("projects")) {
            if (key.equals(line.get("projectKey").asText())) return line;
        }
        throw new AssertionError("No project line for " + key);
    }

    private String employeeId(Session s) throws Exception {
        return getJson("/api/v1/people/me", s).get("id").asText();
    }

    private void setSalary(Session s, String employeeId, long annual) throws Exception {
        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/compensation")
                        .header("Authorization", bearer(s)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("annualAmount", annual, "reason", "Test salary"))))
                .andExpect(status().isOk());
    }

    private String createClient(Session s, String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/clients").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String createProject(Session s, String name, String key) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/work/projects").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "key", key))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTask(Session s, String projectId, String title, String assigneeId, Integer points)
            throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("title", title);
        body.put("assigneeId", assigneeId);
        if (points != null) body.put("storyPoints", points);
        MvcResult r = mockMvc.perform(post("/api/v1/work/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(s)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private void finish(Session s, String taskId) throws Exception {
        patchJson("/api/v1/work/tasks/" + taskId, s, Map.of("status", "DONE"));
    }

    private Session addMember(Session owner, String email) throws Exception {
        mockMvc.perform(post("/api/v1/invitations").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", "MEMBER"))))
                .andExpect(status().isCreated());
        String token = email().lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "Mem", "lastName", "Ber",
                                "password", PW))))
                .andExpect(status().isOk());
        return login(email, PW);
    }

    private void patchJson(String path, Session s, Map<String, ?> body) throws Exception {
        mockMvc.perform(patch(path).header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }
}
