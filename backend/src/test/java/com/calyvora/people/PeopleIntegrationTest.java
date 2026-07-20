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

class PeopleIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    /** Onboard an owner, invite+accept a member, return the two sessions. */
    private Session[] companyWithMember(String owner, String member) throws Exception {
        Session ownerS = onboardOwner("Acme", owner, PW);
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerS.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", member, "role", "MEMBER"))))
                .andExpect(status().isCreated());
        String token = email.lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "Mem", "lastName", "Ber", "password", PW))))
                .andExpect(status().isOk());
        return new Session[]{ownerS, login(member, PW)};
    }

    private String employeeIdByEmail(Session session, String email) throws Exception {
        JsonNode dir = getJson("/api/v1/people/employees", session);
        for (JsonNode e : dir) {
            if (e.get("email").asText().equals(email)) {
                return e.get("id").asText();
            }
        }
        throw new AssertionError("employee not found: " + email);
    }

    @Test
    void directory_auto_provisions_profiles_for_all_members() throws Exception {
        Session[] s = companyWithMember("owner@acme.com", "mem@acme.com");
        JsonNode dir = getJson("/api/v1/people/employees", s[0]);
        assertThat(dir.size()).isEqualTo(2);
        // Every entry has an employee id and the default ACTIVE status.
        for (JsonNode e : dir) {
            assertThat(e.get("id").asText()).isNotBlank();
            assertThat(e.get("employmentStatus").asText()).isEqualTo("ACTIVE");
        }
    }

    @Test
    void admin_can_update_an_employee_profile() throws Exception {
        Session[] s = companyWithMember("owner2@acme.com", "mem2@acme.com");
        String memberEmpId = employeeIdByEmail(s[0], "mem2@acme.com");

        mockMvc.perform(patch("/api/v1/people/employees/" + memberEmpId)
                        .header("Authorization", "Bearer " + s[0].accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("jobTitle", "Engineer", "employmentType", "FULL_TIME",
                                "startDate", "2026-01-15"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Engineer"))
                .andExpect(jsonPath("$.employmentType").value("FULL_TIME"))
                .andExpect(jsonPath("$.startDate").value("2026-01-15"));
    }

    @Test
    void member_can_read_directory_but_cannot_edit_others() throws Exception {
        Session[] s = companyWithMember("owner3@acme.com", "mem3@acme.com");
        String ownerEmpId = employeeIdByEmail(s[0], "owner3@acme.com");

        // member can browse
        mockMvc.perform(get("/api/v1/people/employees")
                        .header("Authorization", "Bearer " + s[1].accessToken()))
                .andExpect(status().isOk());

        // but cannot edit someone else's profile
        mockMvc.perform(patch("/api/v1/people/employees/" + ownerEmpId)
                        .header("Authorization", "Bearer " + s[1].accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("jobTitle", "CEO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void member_can_self_serve_own_profile() throws Exception {
        Session[] s = companyWithMember("owner4@acme.com", "mem4@acme.com");
        mockMvc.perform(patch("/api/v1/people/me")
                        .header("Authorization", "Bearer " + s[1].accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", "+1-555-0100", "workLocation", "Remote"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+1-555-0100"))
                .andExpect(jsonPath("$.email").value("mem4@acme.com"));
    }

    @Test
    void employees_are_not_visible_across_tenants() throws Exception {
        Session ownerA = onboardOwner("Company A", "a@a.com", PW);
        Session ownerB = onboardOwner("Company B", "b@b.com", PW);
        String aEmpId = employeeIdByEmail(ownerA, "a@a.com");

        // B cannot fetch A's employee by id
        mockMvc.perform(get("/api/v1/people/employees/" + aEmpId)
                        .header("Authorization", "Bearer " + ownerB.accessToken()))
                .andExpect(status().isNotFound());

        // B's directory shows only B
        JsonNode bDir = getJson("/api/v1/people/employees", ownerB);
        assertThat(bDir.size()).isEqualTo(1);
        assertThat(bDir.get(0).get("email").asText()).isEqualTo("b@b.com");
    }
}
