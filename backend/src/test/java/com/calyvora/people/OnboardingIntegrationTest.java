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

class OnboardingIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private Session addMember(Session owner, String email) throws Exception {
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", "MEMBER"))))
                .andExpect(status().isCreated());
        String token = email().lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "New", "lastName", "Hire", "password", PW))))
                .andExpect(status().isOk());
        return login(email, PW);
    }

    private String myEmployeeId(Session s) throws Exception {
        return getJson("/api/v1/people/me", s).get("id").asText();
    }

    @Test
    void admin_seeds_defaults_and_new_hire_completes_own_tasks() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session hire = addMember(owner, "hire@acme.com");
        String hireEmpId = myEmployeeId(hire);

        // admin seeds the default checklist
        mockMvc.perform(post("/api/v1/people/employees/" + hireEmpId + "/onboarding/seed-defaults")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        // the new hire can see their own checklist
        JsonNode tasks = getJson("/api/v1/people/employees/" + hireEmpId + "/onboarding", hire);
        assertThat(tasks.size()).isEqualTo(5);
        String firstTaskId = tasks.get(0).get("id").asText();

        // and complete an item
        mockMvc.perform(patch("/api/v1/people/onboarding/" + firstTaskId)
                        .header("Authorization", "Bearer " + hire.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("completed", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    void member_cannot_view_another_employees_checklist() throws Exception {
        Session owner = onboardOwner("Acme", "owner2@acme.com", PW);
        Session hire = addMember(owner, "hire2@acme.com");
        String ownerEmpId = myEmployeeId(owner);

        mockMvc.perform(get("/api/v1/people/employees/" + ownerEmpId + "/onboarding")
                        .header("Authorization", "Bearer " + hire.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void member_cannot_add_tasks() throws Exception {
        Session owner = onboardOwner("Acme", "owner3@acme.com", PW);
        Session hire = addMember(owner, "hire3@acme.com");
        String hireEmpId = myEmployeeId(hire);

        mockMvc.perform(post("/api/v1/people/employees/" + hireEmpId + "/onboarding")
                        .header("Authorization", "Bearer " + hire.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Give myself a raise"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void checklists_are_isolated_across_tenants() throws Exception {
        Session ownerA = onboardOwner("Company A", "a@a.com", PW);
        Session ownerB = onboardOwner("Company B", "b@b.com", PW);
        String aEmpId = myEmployeeId(ownerA);

        mockMvc.perform(post("/api/v1/people/employees/" + aEmpId + "/onboarding")
                        .header("Authorization", "Bearer " + ownerB.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "cross-tenant task"))))
                .andExpect(status().isNotFound());
    }
}
