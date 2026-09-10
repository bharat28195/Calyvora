package com.calyvora.rls;

import com.calyvora.support.IntegrationTestBase;
import com.calyvora.support.RlsRoleConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole application, run as a role that Row-Level Security applies to.
 *
 * <p>Every other test in this suite runs as a Postgres SUPERUSER, which bypasses RLS entirely, so the
 * policies in V12 are inert and the tenant binding in {@code TenantAwareDataSource} is decorative.
 * That is not a small gap. It is the reason V45 blocked every deploy for weeks behind a green suite,
 * the reason the scale seeder failed only in production, and the reason V30 answered the same problem
 * by switching a table's isolation off.
 *
 * <p>What this class is for is the failure mode that has no symptom: a write on a path where no
 * tenant is bound is not slow or wrong, it is <em>refused</em> — and a read on such a path returns
 * nothing at all while reporting success. Under a superuser both look exactly like working code.
 */
@Import(RlsRoleConfig.class)
class RlsRuntimeTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource dataSource;

    /**
     * That this class is testing what it claims to be testing.
     *
     * <p>Every other assertion here would pass just as happily against a superuser session, where the
     * policies are inert. Without this one, quietly losing the wrapper would turn the whole class into
     * four tests that cannot fail, and nothing would say so. That has already happened once here:
     * {@code FlywayUnderRlsTest} planted a company but no user, so the insert it was written to guard
     * was never attempted, and it would have passed against the broken migration it exists to catch.
     */
    @Test
    void the_session_really_has_no_way_around_the_policies() throws Exception {
        try (java.sql.Connection c = dataSource.getConnection();
             java.sql.Statement st = c.createStatement();
             java.sql.ResultSet rs = st.executeQuery(
                     "select current_user as who,"
                             + " (select rolsuper from pg_roles where rolname = current_user) as is_super,"
                             + " (select rolbypassrls from pg_roles where rolname = current_user) as bypasses")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("who")).isEqualTo(com.calyvora.support.RlsRoleDataSource.ROLE);
            assertThat(rs.getBoolean("is_super")).as("a superuser bypasses RLS entirely").isFalse();
            assertThat(rs.getBoolean("bypasses")).as("BYPASSRLS does the same").isFalse();
        }
    }

    @Test
    void registering_a_workspace_writes_every_tenant_owned_row_it_claims_to() throws Exception {
        // Registration inserts a company, its settings and — since PD-34 — the founder's employee
        // profile, and the profile is the one that lands in an RLS-forced table. Under a superuser
        // this passes whether or not the tenant is bound.
        Session owner = onboardOwner("Rls Ltd", "founder@rls.test", PW);

        JsonNode directory = getJson("/api/v1/people/employees", owner);
        assertThat(directory.size())
                .as("the founder must appear in their own directory")
                .isEqualTo(1);
        assertThat(directory.get(0).get("email").asText()).isEqualTo("founder@rls.test");
    }

    @Test
    void accepting_an_invitation_provisions_the_profile_with_no_tenant_bound() throws Exception {
        Session owner = onboardOwner("Rls Ltd", "founder@rls.test", PW);

        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "joiner@rls.test", "role", "MEMBER"))))
                .andExpect(status().isCreated());

        // The accept endpoint is public: no authentication, and therefore no tenant bound. This is
        // exactly the case the old code said could not create a profile, and the case TenantBinder
        // exists to answer. If it is wrong, this call still returns 200 and the person simply never
        // appears anywhere — which is why the assertion is on the directory, not on the status code.
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", email.lastInvitationToken(),
                                "firstName", "Jo", "lastName", "Iner", "password", PW))))
                .andExpect(status().isOk());

        JsonNode directory = getJson("/api/v1/people/employees", owner);
        assertThat(directory.size()).isEqualTo(2);
        assertThat(directory.findValuesAsText("email")).contains("joiner@rls.test");
    }

    @Test
    void a_tenant_cannot_read_another_tenants_people() throws Exception {
        Session acme = onboardOwner("Acme", "owner@acme.test", PW);
        Session other = onboardOwner("Umbrella", "owner@umbrella.test", PW);

        // The claim RLS is actually there to make. Worth asserting under a role it applies to, since
        // under a superuser it would pass on the application's filtering alone and prove nothing
        // about the database layer beneath it.
        JsonNode acmeDir = getJson("/api/v1/people/employees", acme);
        assertThat(acmeDir.findValuesAsText("email"))
                .containsExactly("owner@acme.test");

        JsonNode otherDir = getJson("/api/v1/people/employees", other);
        assertThat(otherDir.findValuesAsText("email"))
                .containsExactly("owner@umbrella.test");
    }

    @Test
    void the_demo_seed_writes_a_whole_company_under_policy() throws Exception {
        // The seed binds its tenant by hand rather than through a request, which is the same shape as
        // the scale seeder that failed in production and passed here. Nothing asserts the count but
        // the seed itself: if a single insert is refused, the request fails outright.
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session ava = login("ava.chen@northwind.demo", "demopass123");

        JsonNode directory = getJson("/api/v1/people/employees", ava);
        assertThat(directory.size()).isEqualTo(7);
    }
}
