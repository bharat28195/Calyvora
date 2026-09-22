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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Getting a customer's data out, and getting a customer out.
 *
 * <p>The assertion that matters is completeness. A delete that empties most of a customer's tables
 * and reports success is worse than one that fails: the erasure request is answered, the personal
 * data is still there, and nobody finds out. So this counts rows across every tenant-owned table
 * before and after, from the catalogue rather than from a list written here — a list would agree
 * with whatever the code forgot.
 *
 * <p>Runs without the restricted role: deleting a whole company is not something the tenant policy
 * is meant to permit, and counting across tenants to prove the neighbour survived needs to see both.
 */
@TestPropertySource(properties = "calyvora.test.rls-role=false")
class TenantExportDeleteTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Autowired
    private PlatformOwnerBootstrap platformOwnerBootstrap;

    @Autowired
    private JdbcTemplate jdbc;

    private Session platformOwner() throws Exception {
        platformOwnerBootstrap.ensurePlatformOwner();
        return login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
    }

    private String demoCompanyId(Session platform) throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        for (JsonNode c : getJson("/api/v1/platform/companies", platform)) {
            if ("Northwind Robotics".equals(c.get("name").asText())) {
                return c.get("companyId").asText();
            }
        }
        throw new AssertionError("the demo company was not provisioned");
    }

    /** Rows this company owns, across every table that carries a company_id. */
    private long rowsOwnedBy(UUID companyId) {
        long total = 0;
        for (String table : jdbc.queryForList(
                "select c.relname from pg_attribute a join pg_class c on c.oid = a.attrelid "
                        + "join pg_namespace n on n.oid = c.relnamespace "
                        + "where a.attname = 'company_id' and c.relkind = 'r' "
                        + "and n.nspname = current_schema() and a.attnum > 0", String.class)) {
            Long n = jdbc.queryForObject("select count(*) from \"" + table + "\" where company_id = ?",
                    Long.class, companyId);
            total += n == null ? 0 : n;
        }
        return total;
    }

    @Test
    @DisplayName("the export carries the company's rows, and no session credentials")
    void export_is_the_customers_data() throws Exception {
        Session platform = platformOwner();
        String companyId = demoCompanyId(platform);

        JsonNode export = getJson("/api/v1/platform/companies/" + companyId + "/export", platform);

        assertThat(export.get("export").get("companyName").asText()).isEqualTo("Northwind Robotics");
        assertThat(export.get("export").get("rows").asLong())
                .as("a seeded company has data; an export of nothing would prove nothing")
                .isGreaterThan(50);

        JsonNode data = export.get("data");
        assertThat(data.has("employees")).isTrue();
        assertThat(data.has("companies")).as("the company row itself carries no company_id").isTrue();
        assertThat(data.get("employees").size()).isEqualTo(7);

        // Live session material is not records. Exporting it would hand somebody a file containing
        // usable credentials for every signed-in employee.
        assertThat(data.has("refresh_tokens")).as("refresh tokens are credentials, not data").isFalse();
    }

    @Test
    @DisplayName("an export contains one company's rows and not its neighbour's")
    void export_does_not_leak_across_tenants() throws Exception {
        Session platform = platformOwner();
        String companyId = demoCompanyId(platform);
        onboardOwner("Bystander Ltd", "owner@bystander.test", "Passw0rd!x");

        JsonNode export = getJson("/api/v1/platform/companies/" + companyId + "/export", platform);
        // The platform's connection is not bound to the customer's tenant, so the where-clause in
        // each query is doing the work the policy would otherwise do. Worth checking rather than
        // assuming.
        for (JsonNode row : export.get("data").get("companies")) {
            assertThat(row.get("name").asText()).isEqualTo("Northwind Robotics");
        }
        assertThat(export.get("data").get("users").toString())
                .doesNotContain("owner@bystander.test");
    }

    @Test
    @DisplayName("deleting a company removes every row it owned, across every table")
    void delete_is_complete() throws Exception {
        Session platform = platformOwner();
        String companyId = demoCompanyId(platform);
        UUID id = UUID.fromString(companyId);

        long before = rowsOwnedBy(id);
        assertThat(before).as("the company must own rows, or completeness is vacuous").isGreaterThan(50);

        mockMvc.perform(delete("/api/v1/platform/companies/" + companyId)
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmName", "Northwind Robotics"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").value((int) before));

        // The whole point: not "most tables", every table.
        assertThat(rowsOwnedBy(id))
                .as("a delete that leaves rows behind answers an erasure request without honouring it")
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from companies where id = ?", Long.class, id))
                .isZero();
    }

    @Test
    @DisplayName("deleting one company leaves the others untouched")
    void delete_stops_at_the_tenant_boundary() throws Exception {
        Session platform = platformOwner();
        String companyId = demoCompanyId(platform);
        Session bystander = onboardOwner("Bystander Two", "owner@bystander2.test", "Passw0rd!x");
        UUID bystanderId = UUID.fromString(
                getJson("/api/v1/auth/me", bystander).get("company").get("id").asText());
        long bystanderRows = rowsOwnedBy(bystanderId);

        mockMvc.perform(delete("/api/v1/platform/companies/" + companyId)
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmName", "Northwind Robotics"))))
                .andExpect(status().isOk());

        assertThat(rowsOwnedBy(bystanderId))
                .as("a cascade that reached past its own tenant would be the worst bug in the product")
                .isEqualTo(bystanderRows);
    }

    @Test
    @DisplayName("the name has to be typed back, exactly")
    void deletion_cannot_be_a_single_gesture() throws Exception {
        Session platform = platformOwner();
        String companyId = demoCompanyId(platform);

        // A console listing every customer is one mis-click from ending one of them.
        mockMvc.perform(delete("/api/v1/platform/companies/" + companyId)
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/platform/companies/" + companyId)
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmName", "Northwind"))))
                .andExpect(status().isBadRequest());

        // Still there.
        assertThat(jdbc.queryForObject("select count(*) from companies where id = ?", Long.class,
                UUID.fromString(companyId))).isEqualTo(1L);
    }

    @Test
    @DisplayName("only the platform owner can export or delete a customer")
    void a_company_admin_cannot_reach_either() throws Exception {
        Session platform = platformOwner();
        String companyId = demoCompanyId(platform);
        Session ava = login("ava.chen@northwind.demo", PW);

        // Their own company — and still refused. These are the vendor's tools.
        mockMvc.perform(get("/api/v1/platform/companies/" + companyId + "/export")
                        .header("Authorization", "Bearer " + ava.accessToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/platform/companies/" + companyId)
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmName", "Northwind Robotics"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the platform's own workspace cannot be deleted")
    void the_vendor_cannot_delete_itself() throws Exception {
        Session platform = platformOwner();
        String platformCompanyId = jdbc.queryForObject(
                "select id::text from companies where is_platform = true", String.class);
        String name = jdbc.queryForObject(
                "select name from companies where is_platform = true", String.class);

        // It holds the owner account: deleting it locks everybody out of the console, including
        // whoever would have to undo it.
        mockMvc.perform(delete("/api/v1/platform/companies/" + platformCompanyId)
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmName", name))))
                .andExpect(status().isBadRequest());
    }
}
