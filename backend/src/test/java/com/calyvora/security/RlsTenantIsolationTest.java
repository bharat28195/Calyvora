package com.calyvora.security;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the database itself enforces tenant isolation (SD-2, V12) — the defense-in-depth layer
 * beneath {@code TenantContext}. Superusers bypass RLS by design, and the embedded test DB connects
 * as one, so the check drops to a NOSUPERUSER role via {@code SET ROLE} (which IS subject to RLS)
 * and drives visibility purely through the {@code calyvora.company_id} session GUC.
 */
class RlsTenantIsolationTest extends IntegrationTestBase {

    private static final String PW = "password1234";
    private static final String ROLE = "calyvora_rls_test";

    @Autowired
    private DataSource dataSource;

    @Test
    void rls_confines_reads_and_writes_to_the_bound_tenant() throws Exception {
        // Two tenants with distinguishable data: A has 2 departments, B has 1.
        var ownerA = onboardOwner("Company A", "a@a.com", PW);
        createDept(ownerA.accessToken(), "Alpha-Eng");
        createDept(ownerA.accessToken(), "Alpha-Design");
        var ownerB = onboardOwner("Company B", "b@b.com", PW);
        createDept(ownerB.accessToken(), "Bravo-Sales");

        try (Connection conn = dataSource.getConnection()) {
            UUID companyA = queryUuid(conn, "select company_id from departments where name = 'Alpha-Eng'");
            UUID companyB = queryUuid(conn, "select company_id from departments where name = 'Bravo-Sales'");
            assertThat(companyA).isNotEqualTo(companyB);

            createRestrictedRole(conn);
            try (Statement st = conn.createStatement()) {
                st.execute("set role " + ROLE);   // now subject to RLS

                // Bound to A: sees only A's two rows, never B's.
                bindTenant(conn, companyA);
                assertThat(countDepartments(conn)).isEqualTo(2);
                assertThat(distinctCompanyIds(conn)).containsExactly(companyA);

                // Bound to B: sees only B's single row.
                bindTenant(conn, companyB);
                assertThat(countDepartments(conn)).isEqualTo(1);
                assertThat(distinctCompanyIds(conn)).containsExactly(companyB);

                // No tenant bound → deny-by-default: nothing is visible.
                bindTenantEmpty(conn);
                assertThat(countDepartments(conn)).isZero();

                // WITH CHECK: bound to A, planting a row tagged B is rejected by the policy.
                bindTenant(conn, companyA);
                assertThatThrownBy(() -> insertDept(conn, companyB, "smuggled"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            } finally {
                dropRestrictedRole(conn);
            }
        }
    }

    // --- helpers ---------------------------------------------------------------

    private void createDept(String accessToken, String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/people/departments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(java.util.Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
    }

    private static void bindTenant(Connection conn, UUID tenant) throws SQLException {
        setConfig(conn, tenant.toString());
    }

    private static void bindTenantEmpty(Connection conn) throws SQLException {
        setConfig(conn, "");
    }

    private static void setConfig(Connection conn, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("select set_config('calyvora.company_id', ?, false)")) {
            ps.setString(1, value);
            ps.execute();
        }
    }

    private static long countDepartments(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select count(*) from departments")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static java.util.List<UUID> distinctCompanyIds(Connection conn) throws SQLException {
        var ids = new java.util.ArrayList<UUID>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select distinct company_id from departments")) {
            while (rs.next()) {
                ids.add(rs.getObject(1, UUID.class));
            }
        }
        return ids;
    }

    private static void insertDept(Connection conn, UUID companyId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "insert into departments (id, company_id, name) values (gen_random_uuid(), ?, ?)")) {
            ps.setObject(1, companyId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private static UUID queryUuid(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getObject(1, UUID.class);
        }
    }

    private static void createRestrictedRole(Connection conn) throws SQLException {
        dropRestrictedRole(conn);
        try (Statement st = conn.createStatement()) {
            st.execute("create role " + ROLE + " nosuperuser");
            st.execute("grant usage on schema public to " + ROLE);
            st.execute("grant select, insert, update, delete on all tables in schema public to " + ROLE);
        }
    }

    private static void dropRestrictedRole(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("reset role");
            st.execute("do $$ begin "
                    + "if exists (select from pg_roles where rolname = '" + ROLE + "') then "
                    + "execute 'drop owned by " + ROLE + "'; execute 'drop role " + ROLE + "'; "
                    + "end if; end $$");
        }
    }
}
