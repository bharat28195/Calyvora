package com.calyvora.expense;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Expense claims: submit → approve → reimburse, edit windows, RBAC and notifications. */
class ExpenseIntegrationTest extends IntegrationTestBase {

    private static final String DEMO_PW = "demopass123";

    @Test
    void submit_approve_then_reimburse_moves_the_totals() throws Exception {
        // A clean company, so the totals are only about the claim this test makes.
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");
        String claimId = submit(owner, "Client visit — flights", "TRAVEL", 12500);

        JsonNode mine = getJson("/api/v1/expenses/me", owner);
        assertThat(mine.get("claims").size()).isEqualTo(1);
        assertThat(mine.get("pendingAmount").asDouble()).isEqualTo(12500.0);
        assertThat(mine.get("awaitingReimbursement").asDouble()).isZero();

        mockMvc.perform(post("/api/v1/expenses/" + claimId + "/approve").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        mine = getJson("/api/v1/expenses/me", owner);
        assertThat(mine.get("pendingAmount").asDouble()).isZero();
        assertThat(mine.get("awaitingReimbursement").asDouble()).isEqualTo(12500.0);

        mockMvc.perform(post("/api/v1/expenses/" + claimId + "/reimburse").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        mine = getJson("/api/v1/expenses/me", owner);
        assertThat(mine.get("awaitingReimbursement").asDouble()).isZero();
        assertThat(mine.get("reimbursedThisYear").asDouble()).isEqualTo(12500.0);
        assertThat(mine.get("claims").get(0).get("status").asText()).isEqualTo("REIMBURSED");
    }

    @Test
    void the_claim_reaches_an_approver_and_the_decision_comes_back() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);   // reports to Marcus
        String claimId = submit(priya, "Team lunch", "MEALS", 3200);

        // The demo seeds its own claims, so look for the one this test made rather than a total.
        Session marcus = login("marcus.reed@northwind.demo", DEMO_PW);
        assertThat(titles(getJson("/api/v1/notifications", marcus)))
                .anyMatch(t -> t.contains("Priya Nair") && t.contains("3200"));

        mockMvc.perform(post("/api/v1/expenses/" + claimId + "/reject").header("Authorization", bearer(marcus))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("note", "Out of policy"))))
                .andExpect(status().isOk());

        assertThat(titles(getJson("/api/v1/notifications", priya))).anyMatch(t -> t.contains("declined"));
        for (JsonNode c : getJson("/api/v1/expenses/me", priya).get("claims")) {
            if ("Team lunch".equals(c.get("title").asText())) {
                assertThat(c.get("decisionNote").asText()).isEqualTo("Out of policy");
            }
        }
    }

    @Test
    void a_claim_can_be_edited_until_it_is_decided() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        String claimId = submit(priya, "Taxi", "TRAVEL", 400);

        mockMvc.perform(patch("/api/v1/expenses/" + claimId).header("Authorization", bearer(priya))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", 650))))
                .andExpect(status().isOk());
        assertThat(getJson("/api/v1/expenses/me", priya).get("claims").get(0).get("amount").asDouble())
                .isEqualTo(650.0);

        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        mockMvc.perform(post("/api/v1/expenses/" + claimId + "/approve").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        // once decided, editing the amount would make the decision a lie
        mockMvc.perform(patch("/api/v1/expenses/" + claimId).header("Authorization", bearer(priya))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", 9999))))
                .andExpect(status().isConflict());
    }

    @Test
    void you_can_only_touch_your_own_claims_and_only_admins_decide() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        Session leo = login("leo.martins@northwind.demo", DEMO_PW);
        String claimId = submit(priya, "Monitor", "SUPPLIES", 8000);

        mockMvc.perform(patch("/api/v1/expenses/" + claimId).header("Authorization", bearer(leo))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", 1))))
                .andExpect(status().isForbidden());

        // a member can't approve, and can't see the company-wide queue
        mockMvc.perform(post("/api/v1/expenses/" + claimId + "/approve").header("Authorization", bearer(leo)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/expenses").header("Authorization", bearer(leo)))
                .andExpect(status().isForbidden());

        // and only sees their own claims in /me — never Priya's
        for (JsonNode c : getJson("/api/v1/expenses/me", leo).get("claims")) {
            assertThat(c.get("employeeName").asText()).isEqualTo("Leo Martins");
        }
    }

    @Test
    void a_lead_decides_their_own_report_and_nobody_else() throws Exception {
        seedDemo();
        // Priya is a senior engineer, not an admin. Dev is her intern. Leo reports to Ava, not Priya.
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        Session dev = login("dev.sharma@northwind.demo", DEMO_PW);
        Session leo = login("leo.martins@northwind.demo", DEMO_PW);

        String mine = submit(dev, "Client visit taxi", "TRAVEL", 1200);
        String notMine = submit(leo, "Design conference", "TRAVEL", 9000);

        // The point of the change: a lead can now clear their own report's claim. Before this the
        // endpoint was gated on OWNER/ADMIN, so the team screen could only watch.
        mockMvc.perform(post("/api/v1/expenses/" + mine + "/approve").header("Authorization", bearer(priya)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // And the half that matters more: opening it by role alone would have let any lead approve
        // anyone's spending in the company. The tree, not the title, is what refuses here.
        mockMvc.perform(post("/api/v1/expenses/" + notMine + "/approve").header("Authorization", bearer(priya)))
                .andExpect(status().isForbidden());

        // Approving is the manager's judgement; paying is finance's. A lead still cannot reimburse.
        mockMvc.perform(post("/api/v1/expenses/" + mine + "/reimburse").header("Authorization", bearer(priya)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rubbish_claims_are_rejected_up_front() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", "password1234");

        mockMvc.perform(post("/api/v1/expenses").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Nothing", "amount", 0))))
                .andExpect(status().isBadRequest());

        Map<String, Object> future = new HashMap<>();
        future.put("title", "Time travel");
        future.put("amount", 100);
        future.put("spentOn", LocalDate.now().plusDays(3).toString());
        mockMvc.perform(post("/api/v1/expenses").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(future)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reimbursing_requires_approval_first() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        String claimId = submit(priya, "Conference ticket", "TRAINING", 15000);

        Session owner = login("ava.chen@northwind.demo", DEMO_PW);
        mockMvc.perform(post("/api/v1/expenses/" + claimId + "/reimburse").header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
    }

    // ---- helpers ----

    private void seedDemo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    /** Notification titles, so assertions can look for the one this test caused. */
    private static List<String> titles(JsonNode notifications) {
        List<String> out = new ArrayList<>();
        notifications.forEach(n -> out.add(n.get("title").asText()));
        return out;
    }

    private String submit(Session s, String title, String category, double amount) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/expenses").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", title, "category", category, "amount", amount,
                                "spentOn", LocalDate.now().minusDays(2).toString()))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }
}
