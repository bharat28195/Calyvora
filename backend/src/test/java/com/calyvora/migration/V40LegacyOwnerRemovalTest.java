package com.calyvora.migration;

import com.calyvora.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V40 removes the demo platform owner. It passed every test and then failed the real deployment, with
 * a foreign key violation from {@code refresh_tokens} — because the test databases were fresh and
 * nothing had ever pointed at that account, whereas on the deployment it had been *used*.
 *
 * <p>That is the gap this class exists to close. The ordinary suite builds a schema and never
 * migrates over data, so a migration that only breaks in the presence of data cannot fail there. Here
 * the schema is taken to V39, realistic rows are planted, and only then is V40 applied.
 *
 * <p>Worth keeping for the next destructive migration as much as for this one.
 */
class V40LegacyOwnerRemovalTest extends IntegrationTestBase {

    private static final String LEGACY = "owner@priorityhr.app";

    @Autowired
    private DataSource dataSource;

    @Test
    void an_unused_legacy_owner_is_deleted_outright() throws Exception {
        JdbcTemplate jdbc = freshSchemaAt39();
        UUID userId = plantLegacyOwner(jdbc);

        migrateTo("40");

        assertThat(countLegacy(jdbc)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, userId))
                .isZero();
    }

    /** The exact shape that broke the deployment: the account had signed in at least once. */
    @Test
    void a_legacy_owner_with_a_live_session_has_it_revoked_and_is_still_deleted() throws Exception {
        JdbcTemplate jdbc = freshSchemaAt39();
        UUID userId = plantLegacyOwner(jdbc);
        jdbc.update("""
                insert into refresh_tokens (id, user_id, token_hash, family_id, expires_at)
                values (?, ?, ?, ?, now() + interval '30 days')
                """, UUID.randomUUID(), userId, "hash-of-a-live-session", UUID.randomUUID());

        migrateTo("40");

        // Clearing the session first is what unblocks the delete — and revoking it is right anyway.
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?",
                Integer.class, userId)).isZero();
        assertThat(countLegacy(jdbc)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, userId))
                .isZero();
    }

    /**
     * When the account authored something the schema will not let go of, deletion is refused — and
     * the migration must not fail. Retiring it reaches the same end: the credential stops working and
     * the email is freed for the real owner, while the audit trail stays intact.
     */
    @Test
    void a_legacy_owner_that_authored_something_is_retired_rather_than_deleted() throws Exception {
        JdbcTemplate jdbc = freshSchemaAt39();
        UUID userId = plantLegacyOwner(jdbc);
        UUID companyId = jdbc.queryForObject("select company_id from users where id = ?", UUID.class, userId);
        // invitations.invited_by is NOT NULL and has no cascade — one of a dozen such columns.
        jdbc.update("""
                insert into invitations (id, company_id, email, role, token_hash, status, invited_by, expires_at)
                values (?, ?, 'someone@example.test', 'MEMBER', 'a-hash', 'ACCEPTED', ?, now() + interval '7 days')
                """, UUID.randomUUID(), companyId, userId);

        migrateTo("40");

        assertThat(countLegacy(jdbc)).isZero();
        assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, userId))
                .isEqualTo("retired+owner@priorityhr.invalid");
        assertThat(jdbc.queryForObject("select password_hash from users where id = ?", String.class, userId))
                .isNull();
        assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, userId))
                .isEqualTo("DISABLED");
    }

    // ---- helpers ----

    /**
     * Wipe the schema the context started with and rebuild it to V39 — the state a real deployment
     * was in before this release. Zonky refreshes the database after each test method, so nothing
     * here leaks into the next test.
     */
    private JdbcTemplate freshSchemaAt39() {
        flyway("39").clean();
        flyway("39").migrate();
        return new JdbcTemplate(dataSource);
    }

    private void migrateTo(String version) {
        flyway(version).migrate();
    }

    private Flyway flyway(String version) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(version)
                // Enabled only here. Cleaning is exactly what you never want in the application.
                .cleanDisabled(false)
                .load();
    }

    private UUID plantLegacyOwner(JdbcTemplate jdbc) {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into companies (id, name, slug, is_platform) values (?, ?, ?, true)",
                companyId, "Priority HR (platform)", "priority-hr-platform");
        jdbc.update("""
                insert into users (id, company_id, email, password_hash, first_name, last_name, role, status,
                                   email_verified_at)
                values (?, ?, ?, 'a-bcrypt-hash', 'Demo', 'Owner', 'OWNER', 'ACTIVE', now())
                """, userId, companyId, LEGACY);
        return userId;
    }

    private Integer countLegacy(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, LEGACY);
    }
}
