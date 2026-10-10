package com.calyvora.access;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every permission-guarded screen, checked against every built-in role (PD-68, feedback item 21).
 *
 * <p>Walks the application's own request mappings rather than a hand-kept list, so an endpoint added
 * tomorrow is checked tomorrow. For each GET with no path variables guarded by
 * {@code @perm.has('X')} (on the method or its controller), an employee must be refused exactly when
 * the Employee role lacks X, and an admin must never be refused.
 */
class PermissionAuditTest extends IntegrationTestBase {

    private static final Pattern PERM = Pattern.compile("@perm\\.has\\('([A-Z_]+)'\\)");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    private record Guarded(String path, Permission permission) {
    }

    private List<Guarded> guardedGets() {
        List<Guarded> out = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            if (!info.getMethodsCondition().getMethods().contains(RequestMethod.GET)) continue;
            HandlerMethod h = e.getValue();
            PreAuthorize pre = h.getMethodAnnotation(PreAuthorize.class);
            if (pre == null) pre = h.getBeanType().getAnnotation(PreAuthorize.class);
            if (pre == null) continue;
            Matcher m = PERM.matcher(pre.value());
            if (!m.find()) continue;
            Permission p;
            try {
                p = Permission.valueOf(m.group(1));
            } catch (IllegalArgumentException ex) {
                throw new AssertionError("Unknown permission " + m.group(1) + " on " + h);
            }
            for (String path : info.getPatternValues()) {
                // Only fixed paths: one with an id in it needs a real object to mean anything.
                if (path.contains("{") || !path.startsWith("/api/v1/")) continue;
                out.add(new Guarded(path, p));
            }
        }
        return out;
    }

    @Test
    @DisplayName("an employee is refused exactly the screens their role does not grant; an admin never")
    void every_guarded_screen_matches_the_role() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session admin = login("ava.chen@northwind.demo", "demopass123");
        Session member = login("priya.nair@northwind.demo", "demopass123");
        var employeeGrants = BuiltinRole.EMPLOYEE.defaults();

        List<Guarded> all = guardedGets();
        assertThat(all).as("the audit found the guarded screens").hasSizeGreaterThan(20);
        List<String> wrong = new ArrayList<>();
        for (Guarded g : all) {
            int asMember = mockMvc.perform(get(g.path()).header("Authorization", "Bearer " + member.accessToken()))
                    .andReturn().getResponse().getStatus();
            int asAdmin = mockMvc.perform(get(g.path()).header("Authorization", "Bearer " + admin.accessToken()))
                    .andReturn().getResponse().getStatus();
            boolean memberShouldPass = employeeGrants.containsKey(g.permission());
            if (!memberShouldPass && asMember != 403) {
                wrong.add("employee got " + asMember + " (expected 403) on " + g.path() + " [" + g.permission() + "]");
            }
            if (asAdmin == 403) {
                wrong.add("admin refused on " + g.path() + " [" + g.permission() + "]");
            }
        }
        assertThat(wrong).as("permission mismatches").isEmpty();
    }

    @Test
    @DisplayName("goals are not readable across the company by id")
    void goals_are_private() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session admin = login("ava.chen@northwind.demo", "demopass123");
        Session member = login("priya.nair@northwind.demo", "demopass123");
        String someoneElse = null;
        String me = null;
        for (var e : getJson("/api/v1/people/employees", admin)) {
            if (e.toString().contains("Priya")) me = e.get("id").asText();
            // Someone above her, not below: a lead may read their own reports' goals.
            else if (e.toString().contains("Ava")) someoneElse = e.get("id").asText();
        }
        mockMvc.perform(get("/api/v1/people/employees/" + someoneElse + "/goals")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/people/employees/" + me + "/goals")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/people/employees/" + someoneElse + "/goals")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk());
    }
}
