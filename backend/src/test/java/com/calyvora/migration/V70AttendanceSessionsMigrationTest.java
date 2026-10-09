package com.calyvora.migration;

import com.calyvora.support.IntegrationTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V70 rewrites tenant data — it backfills check-in sessions and moves UTC clocks back to India time —
 * on tables under FORCE row level security. A version of it written as plain statements passed every
 * test and would have done nothing on the real database, because Flyway runs with no tenant bound.
 * So this takes the schema to V69, plants a company the way a real one looks, applies V70 and reads
 * the result back with the tenant bound.
 */
class V70AttendanceSessionsMigrationTest extends IntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    @Test
    void existing_days_become_sessions_and_utc_clocks_go_back_to_india() {
        JdbcTemplate jdbc = freshSchemaAt69();
        UUID company = UUID.randomUUID();
        jdbc.update("insert into companies (id, name, slug, status) values (?, 'Co', ?, 'ACTIVE')",
                company, "co-" + company.toString().substring(0, 8));
        UUID onUtc = plantPerson(jdbc, company, "UTC");
        UUID inBerlin = plantPerson(jdbc, company, "Europe/Berlin");
        LocalDate day = LocalDate.of(2026, 10, 8);
        asTenant(jdbc, company, c -> {
            exec(c, "insert into company_settings (company_id, timezone, locale, currency, updated_at) "
                    + "values (?, 'UTC', 'en', 'INR', now())", company);
            exec(c, "insert into attendance_records (id, company_id, employee_id, on_date, status, check_in, check_out) "
                    + "values (?, ?, ?, ?, 'PRESENT', '09:30', '18:00')", UUID.randomUUID(), company, onUtc, day);
            return null;
        });

        flyway("70").migrate();

        asTenant(jdbc, company, c -> {
            assertThat(one(c, "select count(*)::text from attendance_punches where employee_id = ?", onUtc)).isEqualTo("1");
            assertThat(one(c, "select check_in::text || '-' || check_out::text from attendance_punches where employee_id = ?", onUtc))
                    .isEqualTo("09:30:00-18:00:00");
            assertThat(one(c, "select timezone from company_settings where company_id = ?", company)).isEqualTo("Asia/Kolkata");
            assertThat(one(c, "select attendance_rules_from::text from company_settings where company_id = ?", company))
                    .isNotNull();
            assertThat(one(c, "select work_day_minutes::text from company_settings where company_id = ?", company)).isEqualTo("540");
            assertThat(one(c, "select coalesce(timezone, 'none') from employees where id = ?", onUtc)).isEqualTo("none");
            // Somebody who chose another zone made a decision, and it stands.
            assertThat(one(c, "select timezone from employees where id = ?", inBerlin)).isEqualTo("Europe/Berlin");
            return null;
        });
    }

    // ---- helpers ----

    /** A user and their employee record, both with the given personal timezone. Returns the employee id. */
    private UUID plantPerson(JdbcTemplate jdbc, UUID company, String zone) {
        UUID user = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        jdbc.update("insert into users (id, company_id, email, first_name, last_name, role, status, timezone) "
                        + "values (?, ?, ?, 'Test', 'Person', 'EMPLOYEE', 'ACTIVE', ?)",
                user, company, "p-" + user.toString().substring(0, 8) + "@example.test", zone);
        asTenant(jdbc, company, c -> {
            exec(c, "insert into employees (id, company_id, user_id, timezone) values (?, ?, ?, ?)",
                    employee, company, user, zone);
            return null;
        });
        return employee;
    }

    private JdbcTemplate freshSchemaAt69() {
        flyway("69").clean();
        flyway("69").migrate();
        return new JdbcTemplate(dataSource);
    }

    private Flyway flyway(String version) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(version)
                .cleanDisabled(false)   // only here; never in the application
                .load();
    }

    private <T> T asTenant(JdbcTemplate jdbc, UUID company, ConnectionCallback<T> work) {
        return jdbc.execute((ConnectionCallback<T>) c -> {
            exec(c, "select set_config('calyvora.company_id', ?, false)", company.toString());
            try {
                return work.doInConnection(c);
            } finally {
                exec(c, "select set_config('calyvora.company_id', '', false)");
            }
        });
    }

    private static void exec(Connection c, String sql, Object... args) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.execute();
        }
    }

    private static String one(Connection c, String sql, Object... args) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
