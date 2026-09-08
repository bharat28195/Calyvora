package com.calyvora.migration;

import com.calyvora.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Runs the whole migration history the way <em>production</em> runs it: as a role that does not
 * bypass Row-Level Security, against a database that already contains a company.
 *
 * <p><b>Why this test exists.</b> V45 shipped with the RLS policy on {@code leave_policies} created
 * before its own backfill. Every test passed and the deploy died on boot:
 *
 * <pre>ERROR: new row violates row-level security policy for table "leave_policies" (SQLSTATE 42501)</pre>
 *
 * <p>Two conditions have to hold together for that to happen, and the suite had neither:
 *
 * <ul>
 *   <li><b>A role that RLS applies to.</b> The embedded Postgres used by every other test connects as
 *       a SUPERUSER, and a superuser bypasses RLS entirely — so the offending insert sailed through
 *       locally. Neon hands the app a NOSUPERUSER role without BYPASSRLS, which
 *       {@code TenantIsolationVerifier} deliberately REQUIRES at boot, so the policy is live there.</li>
 *   <li><b>Data already in the database.</b> The backfill is {@code insert ... select from companies},
 *       so on an empty schema it inserts zero rows and no policy is ever evaluated. Migrating a fresh
 *       database proves nothing; that is exactly why CI stayed green.</li>
 * </ul>
 *
 * <p>So this migrates to the version before the backfill, plants a company, and then migrates the
 * rest — which is the state every existing customer's database is actually in. Sibling of the V40
 * lesson: a migration that passes 400 tests can still be the thing that takes production down, and
 * the missing coverage is never the SQL syntax, it is the conditions it runs under.
 *
 * <p>Deliberately asserts on the whole history rather than on V45, so the next migration that seeds a
 * tenant-scoped table is covered on the day it is written.
 */
class FlywayUnderRlsTest extends IntegrationTestBase {

    /** The version immediately before the first migration that backfills a tenant-scoped table. */
    private static final String BEFORE_BACKFILL = "44";

    private static final String MIGRATION_ROLE = "calyvora_migration_rls_test";
    private static final String SCRATCH_DB = "flyway_rls_check";

    @Autowired
    private DataSource dataSource;

    @Test
    void the_whole_migration_history_applies_as_a_role_that_cannot_bypass_rls() throws Exception {
        String adminUrl;
        try (Connection admin = dataSource.getConnection()) {
            adminUrl = admin.getMetaData().getURL();
            recreateScratchDatabase(admin);
            createMigrationRole(admin);
        }
        String scratchUrl = swapDatabase(adminUrl, SCRATCH_DB);

        try {
            grantSchemaTo(scratchUrl);

            // 1. Everything up to the point where the schema exists but the backfill has not run.
            migrate(scratchUrl, BEFORE_BACKFILL);

            // 2. A company, planted as the superuser — this is the row every real customer already
            //    has, and the row that makes the backfill actually insert something.
            UUID companyId = plantCompany(scratchUrl);

            // 3. The rest of the history, as the restricted role. This is the assertion: before the
            //    fix it failed here with SQLSTATE 42501 on leave_policies.
            assertThatCode(() -> migrate(scratchUrl, null))
                    .as("migrations must apply as a role subject to RLS, with data already present")
                    .doesNotThrowAnyException();

            // 4. And the backfill must have actually written rows — a policy that silently swallowed
            //    them would leave every existing company with no leave entitlement at all, which is a
            //    quieter and worse failure than the crash.
            assertThat(countLeavePolicies(scratchUrl, companyId))
                    .as("every existing company gets its five seeded leave policies")
                    .isEqualTo(5);
        } finally {
            try (Connection admin = dataSource.getConnection()) {
                dropScratchDatabase(admin);
                dropMigrationRole(admin);
            } catch (SQLException ignored) {
                // Cleanup only — the embedded server is discarded after the test class anyway.
            }
        }
    }

    // --- helpers ---------------------------------------------------------------

    /**
     * Migrates the scratch database as {@link #MIGRATION_ROLE}.
     *
     * <p>Connects as the superuser and immediately drops privileges with {@code SET ROLE} on every
     * connection, rather than authenticating as the restricted role — the embedded server's auth
     * config is not ours to depend on, and {@code SET ROLE} produces the same thing that matters
     * here: a session that RLS applies to.
     */
    private static void migrate(String url, String target) {
        var config = Flyway.configure()
                .dataSource(url, "postgres", "")
                .locations("classpath:db/migration")
                .initSql("set role " + MIGRATION_ROLE);
        if (target != null) {
            config = config.target(org.flywaydb.core.api.MigrationVersion.fromVersion(target));
        }
        config.load().migrate();
    }

    private static UUID plantCompany(String url) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = connect(url); Statement st = c.createStatement()) {
            st.execute("insert into companies (id, name, slug, status) values ('"
                    + id + "', 'Legacy Customer', 'legacy-customer-" + id.toString().substring(0, 8)
                    + "', 'ACTIVE')");
        }
        return id;
    }

    private static int countLeavePolicies(String url, UUID companyId) throws SQLException {
        try (Connection c = connect(url); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select count(*) from leave_policies where company_id = '" + companyId + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void grantSchemaTo(String url) throws SQLException {
        try (Connection c = connect(url); Statement st = c.createStatement()) {
            // Enough to create the schema and write to it, and nothing more — notably NOT BYPASSRLS,
            // which is the entire point of the exercise.
            st.execute("grant all on schema public to " + MIGRATION_ROLE);
            st.execute("grant create on database " + SCRATCH_DB + " to " + MIGRATION_ROLE);
        }
    }

    private static Connection connect(String url) throws SQLException {
        return java.sql.DriverManager.getConnection(url, "postgres", "");
    }

    private static void recreateScratchDatabase(Connection admin) throws SQLException {
        try (Statement st = admin.createStatement()) {
            st.execute("drop database if exists " + SCRATCH_DB);
            st.execute("create database " + SCRATCH_DB);   // cannot run inside a transaction
        }
    }

    private static void dropScratchDatabase(Connection admin) throws SQLException {
        try (Statement st = admin.createStatement()) {
            st.execute("drop database if exists " + SCRATCH_DB);
        }
    }

    private static void createMigrationRole(Connection admin) throws SQLException {
        dropMigrationRole(admin);
        try (Statement st = admin.createStatement()) {
            st.execute("create role " + MIGRATION_ROLE + " nosuperuser nobypassrls");
        }
    }

    private static void dropMigrationRole(Connection admin) throws SQLException {
        try (Statement st = admin.createStatement()) {
            st.execute("do $$ begin if exists (select 1 from pg_roles where rolname = '"
                    + MIGRATION_ROLE + "') then execute 'drop role " + MIGRATION_ROLE
                    + "'; end if; end $$;");
        }
    }

    /** Rewrites the database name in a JDBC URL, keeping host, port and query string. */
    private static String swapDatabase(String url, String database) {
        int lastSlash = url.lastIndexOf('/');
        int query = url.indexOf('?', lastSlash);
        String tail = query < 0 ? "" : url.substring(query);
        return url.substring(0, lastSlash + 1) + database + tail;
    }
}
