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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V59 deletes the made-up PF and income-tax rows from payslip templates. A delete over tenant data is
 * the kind of migration that passes on an empty test schema and does nothing — or too much — on a real
 * one (see V40), so this takes the schema to V58, plants templates the way companies actually have
 * them, and only then applies V59.
 *
 * <p>The rows are written and read with the tenant bound, because payslip_components is under FORCE
 * row level security: a migration that forgot to bind would delete nothing, and a test that forgot
 * would see nothing — both green, both wrong.
 */
class V59TemplateDeductionCleanupTest extends IntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    @Test
    void the_untouched_defaults_are_removed_and_the_earnings_kept() {
        JdbcTemplate jdbc = freshSchemaAt58();
        UUID company = plantCompany(jdbc, "default-template");
        plantDefaultTemplate(jdbc, company);

        flyway("59").migrate();

        assertThat(componentNames(jdbc, company))
                .containsExactly("Basic", "House rent allowance", "Special allowance");
    }

    @Test
    void a_deduction_the_company_changed_is_theirs_and_is_left_alone() {
        JdbcTemplate jdbc = freshSchemaAt58();
        UUID company = plantCompany(jdbc, "custom-template");
        plantDefaultTemplate(jdbc, company);
        asTenant(jdbc, company, c -> {
            // Rate changed from the shipped 10% to 8%, and a deduction of their own.
            exec(c, "update payslip_components set value = 8 where company_id = ? and name = 'Income tax'", company);
            insert(c, company, "Professional tax", "DEDUCTION", "FIXED", 200, false, 5);
            return null;
        });

        flyway("59").migrate();

        assertThat(componentNames(jdbc, company)).containsExactly(
                "Basic", "House rent allowance", "Special allowance", "Income tax", "Professional tax");
    }

    @Test
    void every_company_is_cleaned_not_just_the_first() {
        JdbcTemplate jdbc = freshSchemaAt58();
        UUID a = plantCompany(jdbc, "tenant-a");
        UUID b = plantCompany(jdbc, "tenant-b");
        plantDefaultTemplate(jdbc, a);
        plantDefaultTemplate(jdbc, b);

        flyway("59").migrate();

        assertThat(componentNames(jdbc, a)).hasSize(3);
        assertThat(componentNames(jdbc, b)).hasSize(3);
    }

    // ---- helpers ----

    private JdbcTemplate freshSchemaAt58() {
        flyway("58").clean();
        flyway("58").migrate();
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

    private UUID plantCompany(JdbcTemplate jdbc, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into companies (id, name, slug) values (?, ?, ?)", id, "Co " + slug, slug);
        return id;
    }

    /** Exactly what PayslipTemplateService.seedDefaults wrote before this release. */
    private void plantDefaultTemplate(JdbcTemplate jdbc, UUID company) {
        asTenant(jdbc, company, c -> {
            insert(c, company, "Basic", "EARNING", "PERCENT_OF_GROSS", 50, true, 0);
            insert(c, company, "House rent allowance", "EARNING", "PERCENT_OF_GROSS", 25, false, 1);
            insert(c, company, "Special allowance", "EARNING", "REMAINDER", null, false, 2);
            insert(c, company, "Provident fund", "DEDUCTION", "PERCENT_OF_BASIC", 12, false, 3);
            insert(c, company, "Income tax", "DEDUCTION", "PERCENT_OF_GROSS", 10, false, 4);
            return null;
        });
    }

    private List<String> componentNames(JdbcTemplate jdbc, UUID company) {
        return asTenant(jdbc, company, c -> {
            List<String> names = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "select name from payslip_components where company_id = ? order by sort_order")) {
                ps.setObject(1, company);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) names.add(rs.getString(1));
                }
            }
            return names;
        });
    }

    /** One connection, tenant bound for its duration and cleared after, so nothing leaks to the pool. */
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

    private static void insert(Connection c, UUID company, String name, String kind, String calc,
                               Integer value, boolean basis, int order) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                insert into payslip_components (id, company_id, name, kind, calc, value, is_basis, sort_order)
                values (?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, company);
            ps.setString(3, name);
            ps.setString(4, kind);
            ps.setString(5, calc);
            if (value == null) ps.setNull(6, java.sql.Types.NUMERIC); else ps.setInt(6, value);
            ps.setBoolean(7, basis);
            ps.setInt(8, order);
            ps.executeUpdate();
        }
    }

    private static void exec(Connection c, String sql, Object... args) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.execute();
        }
    }
}
