package com.calyvora.platform;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A customer is archived, not deleted (PD-74): everything kept, nobody signs in, not billed, and
 * restorable. Deleting for good needs the company archived first, its name and the owner's password.
 */
@TestPropertySource(properties = "calyvora.test.rls-role=false")
class CompanyArchiveTest extends IntegrationTestBase {

    @Autowired
    private PlatformOwnerBootstrap platformOwnerBootstrap;
    @Autowired
    private JdbcTemplate jdbc;

    private Session owner() throws Exception {
        platformOwnerBootstrap.ensurePlatformOwner();
        return login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
    }

    private String northwind(Session owner) throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        for (JsonNode c : getJson("/api/v1/platform/companies", owner)) {
            if ("Northwind Robotics".equals(c.get("name").asText())) return c.get("companyId").asText();
        }
        throw new AssertionError("no demo company");
    }

    private int loginStatus(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "demopass123"))))
                .andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
                                                                    Session s, Object body) throws Exception {
        return mockMvc.perform(b.header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    @DisplayName("archiving keeps every row, stops sign-in and billing; restoring brings it all back")
    void archive_and_restore() throws Exception {
        Session owner = owner();
        String id = northwind(owner);
        long employees = jdbc.queryForObject("select count(*) from employees where company_id = ?", Long.class, UUID.fromString(id));

        call(post("/api/v1/platform/companies/" + id + "/archive"), owner, Map.of("reason", "Contract ended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archiveReason").value("Contract ended"))
                .andExpect(jsonPath("$.monthlyRevenue").value(0));
        assertThat(jdbc.queryForObject("select count(*) from employees where company_id = ?", Long.class, UUID.fromString(id)))
                .as("nothing deleted").isEqualTo(employees);
        assertThat(loginStatus("ava.chen@northwind.demo")).as("nobody signs in to an archived workspace").isEqualTo(403);
        assertThat(getJson("/api/v1/platform/companies", owner).toString()).contains("Contract ended");

        call(post("/api/v1/platform/companies/" + id + "/restore"), owner, Map.of()).andExpect(status().isOk());
        assertThat(loginStatus("ava.chen@northwind.demo")).isEqualTo(200);
    }

    @Test
    @DisplayName("a permanent delete needs the company archived, its name, and the owner's password")
    void permanent_delete_is_guarded() throws Exception {
        Session owner = owner();
        String id = northwind(owner);
        Map<String, String> right = Map.of("confirmName", "Northwind Robotics", "ownerPassword", PLATFORM_OWNER_PASSWORD);

        // Live company: archive first.
        call(delete("/api/v1/platform/companies/" + id), owner, right).andExpect(status().isConflict());

        call(post("/api/v1/platform/companies/" + id + "/archive"), owner, Map.of()).andExpect(status().isOk());
        // Wrong password, or no password: refused, nothing gone.
        call(delete("/api/v1/platform/companies/" + id), owner,
                Map.of("confirmName", "Northwind Robotics", "ownerPassword", "not-it")).andExpect(status().isBadRequest());
        call(delete("/api/v1/platform/companies/" + id), owner, Map.of("confirmName", "Northwind Robotics"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from companies where id = ?", Long.class, UUID.fromString(id))).isEqualTo(1L);

        call(delete("/api/v1/platform/companies/" + id), owner, right).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from companies where id = ?", Long.class, UUID.fromString(id))).isZero();
    }
}
