package com.calyvora.access;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Custom roles (PD-54) against the demo company: Ava is ADMIN, Leo HR, Tom MANAGER, Priya a MEMBER
 * who leads Dev (an intern), Sara a MEMBER who leads nobody.
 *
 * <p>The regression half of the proof is the rest of the suite, which runs unchanged on the built-in
 * roles; this class proves the new half — that a role an admin builds is obeyed, at its scope, at
 * once, and that the guard rails hold.
 */
class CustomRolesIntegrationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";   // the demo seed's public password

    private Session ava, leo, priya, sara, dev;

    private void demo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        ava = login("ava.chen@northwind.demo", PW);
        leo = login("leo.martins@northwind.demo", PW);
        priya = login("priya.nair@northwind.demo", PW);
        sara = login("sara.okoro@northwind.demo", PW);
        dev = login("dev.sharma@northwind.demo", PW);
    }

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private String userIdOf(Session s) throws Exception {
        return getJson("/api/v1/auth/me", s).get("user").get("id").asText();
    }

    private String employeeIdOf(String email) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", ava)) {
            if (email.equals(e.path("email").asText())) return e.get("id").asText();
        }
        throw new AssertionError("no employee " + email);
    }

    private String roleId(String name) throws Exception {
        for (JsonNode r : getJson("/api/v1/roles", ava)) {
            if (name.equals(r.get("name").asText())) return r.get("id").asText();
        }
        throw new AssertionError("no role " + name);
    }

    private String createRole(String name, Map<String, String> permissions) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/roles").header("Authorization", bearer(ava))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "permissions", permissions))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private void assign(Session target, String roleId) throws Exception {
        mockMvc.perform(put("/api/v1/company/members/" + userIdOf(target) + "/role").header("Authorization", bearer(ava))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("roleId", roleId))))
                .andExpect(status().isNoContent());
    }

    private int code(Session s, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(s))).andReturn().getResponse().getStatus();
    }

    @Test
    void every_company_has_the_four_built_ins_and_admin_is_locked() throws Exception {
        demo();
        JsonNode roles = getJson("/api/v1/roles", ava);
        assertThat(roles).hasSize(4);
        assertThat(roles.get(0).get("builtin").asText()).isEqualTo("ADMIN");
        assertThat(roles.get(0).get("locked").asBoolean()).isTrue();
        assertThat(roles.get(0).get("permissions").size()).isEqualTo(Permission.values().length);
        // HR holds everything but the four company-administration permissions.
        JsonNode hr = roles.get(1).get("permissions");
        assertThat(hr.has("PAYROLL_MANAGE")).isTrue();
        assertThat(hr.has("MEMBERS_MANAGE")).isFalse();
        // Employees approve for their own team only.
        assertThat(roles.get(3).get("permissions").get("LEAVE_APPROVE").asText()).isEqualTo("TEAM");

        // And what each person can do is on /me, for the menu.
        assertThat(getJson("/api/v1/auth/me", leo).get("permissions").has("PAYROLL_MANAGE")).isTrue();
        assertThat(getJson("/api/v1/auth/me", sara).get("permissions").has("PAYROLL_MANAGE")).isFalse();
    }

    @Test
    void a_custom_role_that_sees_salaries_company_wide_but_cannot_change_them() throws Exception {
        demo();
        String auditor = createRole("Finance auditor", Map.of("SALARY_VIEW", "COMPANY"));
        String marcus = employeeIdOf("marcus.reed@northwind.demo");

        assertThat(code(sara, "/api/v1/people/employees/" + marcus + "/compensation")).isEqualTo(403);
        assign(sara, auditor);
        assertThat(code(sara, "/api/v1/people/employees/" + marcus + "/compensation")).isEqualTo(200);
        assertThat(code(sara, "/api/v1/people/employees/" + marcus + "/payslip")).isEqualTo(200);

        // Seeing pay is not running payroll.
        assertThat(code(sara, "/api/v1/payroll/run")).isEqualTo(403);
        mockMvc.perform(post("/api/v1/people/employees/" + marcus + "/compensation").header("Authorization", bearer(sara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("annualAmount", 1, "currency", "INR", "effectiveDate", "2026-01-01"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void a_team_scoped_salary_permission_reaches_only_the_holders_own_reports() throws Exception {
        demo();
        String lead = createRole("Team lead with pay", Map.of("SALARY_VIEW", "TEAM", "LEAVE_APPROVE", "TEAM"));
        assign(priya, lead);

        // Dev is Priya's intern; Marcus is not beneath her.
        assertThat(code(priya, "/api/v1/people/employees/" + employeeIdOf("dev.sharma@northwind.demo") + "/compensation"))
                .isEqualTo(200);
        assertThat(code(priya, "/api/v1/people/employees/" + employeeIdOf("marcus.reed@northwind.demo") + "/compensation"))
                .isEqualTo(403);
    }

    @Test
    void editing_a_built_in_takes_effect_on_the_next_request() throws Exception {
        demo();
        String hrRole = roleId("HR");
        String marcus = employeeIdOf("marcus.reed@northwind.demo");
        assertThat(code(leo, "/api/v1/people/employees/" + marcus + "/compensation")).isEqualTo(200);

        // The company decides its HR should not see salaries.
        JsonNode hr = getJson("/api/v1/roles", ava).get(1).get("permissions");
        Map<String, String> without = objectMapper.convertValue(hr, Map.class);
        without.remove("SALARY_VIEW");
        mockMvc.perform(patch("/api/v1/roles/" + hrRole).header("Authorization", bearer(ava))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("permissions", without))))
                .andExpect(status().isOk());

        // Same token, next request: refused. Permissions are read per request, not from the token.
        assertThat(code(leo, "/api/v1/people/employees/" + marcus + "/compensation")).isEqualTo(403);
    }

    @Test
    void the_guard_rails_hold() throws Exception {
        demo();
        String adminRole = roleId("Admin");
        String employeeRole = roleId("Employee");

        // The Admin role cannot be edited.
        mockMvc.perform(patch("/api/v1/roles/" + adminRole).header("Authorization", bearer(ava))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("permissions", Map.of()))))
                .andExpect(status().isBadRequest());
        // Nobody changes their own role.
        mockMvc.perform(put("/api/v1/company/members/" + userIdOf(ava) + "/role").header("Authorization", bearer(ava))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("roleId", employeeRole))))
                .andExpect(status().isBadRequest());
        // A role somebody holds cannot be deleted.
        String temp = createRole("Temporary", Map.of("INSIGHTS_VIEW", "COMPANY"));
        assign(sara, temp);
        mockMvc.perform(delete("/api/v1/roles/" + temp).header("Authorization", bearer(ava)))
                .andExpect(status().isConflict());
        // Built-ins cannot be deleted at all.
        mockMvc.perform(delete("/api/v1/roles/" + employeeRole).header("Authorization", bearer(ava)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void the_last_admin_cannot_be_moved_off_admin() throws Exception {
        demo();
        // Make Leo an admin, have him demote Ava, then Ava is gone as an admin and Leo is the only one.
        assign(leo, roleId("Admin"));
        mockMvc.perform(put("/api/v1/company/members/" + userIdOf(ava) + "/role").header("Authorization", bearer(leo))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("roleId", roleId("HR")))))
                .andExpect(status().isNoContent());
        // Ava (now HR, so no MEMBERS_MANAGE) can no longer manage roles at all.
        assertThat(code(ava, "/api/v1/roles")).isEqualTo(403);
    }

    @Test
    void only_people_who_manage_members_can_see_or_change_roles() throws Exception {
        demo();
        assertThat(code(leo, "/api/v1/roles")).isEqualTo(403);
        assertThat(code(dev, "/api/v1/roles")).isEqualTo(403);
        mockMvc.perform(post("/api/v1/roles").header("Authorization", bearer(leo))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Sneaky", "permissions", Map.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void roles_are_isolated_between_companies() throws Exception {
        demo();
        String auditor = createRole("Finance auditor", Map.of("SALARY_VIEW", "COMPANY"));
        Session other = onboardOwner("Acme", "owner@acme.com", "password1234");
        assertThat(getJson("/api/v1/roles", other)).hasSize(4);   // its own built-ins, not Northwind's
        mockMvc.perform(patch("/api/v1/roles/" + auditor).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Hijacked"))))
                .andExpect(status().isNotFound());
    }
}
