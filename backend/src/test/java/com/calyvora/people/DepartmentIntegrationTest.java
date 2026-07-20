package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DepartmentIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private String createDept(Session admin, String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/people/departments")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String employeeId(Session s, String email) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", s)) {
            if (e.get("email").asText().equals(email)) {
                return e.get("id").asText();
            }
        }
        throw new AssertionError("no employee " + email);
    }

    @Test
    void admin_creates_and_lists_departments() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        createDept(owner, "Engineering");
        createDept(owner, "Design");

        JsonNode list = getJson("/api/v1/people/departments", owner);
        assertThat(list.size()).isEqualTo(2);
        // ordered by name: Design, Engineering
        assertThat(list.get(0).get("name").asText()).isEqualTo("Design");
        assertThat(list.get(0).get("memberCount").asLong()).isZero();
    }

    @Test
    void assigning_an_employee_updates_department_member_count() throws Exception {
        Session owner = onboardOwner("Acme", "owner2@acme.com", PW);
        String deptId = createDept(owner, "Engineering");
        String empId = employeeId(owner, "owner2@acme.com");

        mockMvc.perform(patch("/api/v1/people/employees/" + empId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("departmentId", deptId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(deptId));

        JsonNode list = getJson("/api/v1/people/departments", owner);
        assertThat(list.get(0).get("memberCount").asLong()).isEqualTo(1);
    }

    @Test
    void member_cannot_create_a_department() throws Exception {
        Session owner = onboardOwner("Acme", "owner3@acme.com", PW);
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "mem3@acme.com", "role", "MEMBER"))))
                .andExpect(status().isCreated());
        String token = email.lastInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "M", "lastName", "B", "password", PW))))
                .andExpect(status().isOk());
        Session member = login("mem3@acme.com", PW);

        mockMvc.perform(post("/api/v1/people/departments")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Shadow IT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void departments_are_isolated_across_tenants() throws Exception {
        Session ownerA = onboardOwner("Company A", "a@a.com", PW);
        Session ownerB = onboardOwner("Company B", "b@b.com", PW);
        String deptA = createDept(ownerA, "A-Eng");

        // B cannot see A's department, and cannot update it
        JsonNode bList = getJson("/api/v1/people/departments", ownerB);
        assertThat(bList.size()).isZero();
        mockMvc.perform(patch("/api/v1/people/departments/" + deptA)
                        .header("Authorization", bearer(ownerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "hijack"))))
                .andExpect(status().isNotFound());
    }
}
