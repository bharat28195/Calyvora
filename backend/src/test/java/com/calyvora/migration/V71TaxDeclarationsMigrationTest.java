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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V71 moves every declaration onto the new lines, turns a home loan into a house, and tags the HRA
 * line of each salary template — all tenant data under FORCE row level security. Taken to V70 with a
 * company planted the way a real one looks, then migrated, then read back with the tenant bound.
 */
class V71TaxDeclarationsMigrationTest extends IntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    @Test
    void old_lines_become_new_ones_and_nothing_claimed_is_lost() {
        JdbcTemplate jdbc = freshSchemaAt70();
        UUID company = UUID.randomUUID();
        jdbc.update("insert into companies (id, name, slug, status) values (?, 'Co', ?, 'ACTIVE')",
                company, "co-" + company.toString().substring(0, 8));
        UUID user = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        UUID declaration = UUID.randomUUID();
        jdbc.update("insert into users (id, company_id, email, first_name, last_name, role, status) "
                + "values (?, ?, ?, 'Test', 'Person', 'EMPLOYEE', 'ACTIVE')", user, company, "p@example.test");
        asTenant(jdbc, company, c -> {
            exec(c, "insert into employees (id, company_id, user_id) values (?, ?, ?)", employee, company, user);
            exec(c, "insert into tax_declarations (id, company_id, employee_id, financial_year, regime) "
                    + "values (?, ?, ?, '2026-27', 'OLD')", declaration, company, employee);
            item(c, company, declaration, "SECTION_80C", 150000);
            item(c, company, declaration, "SECTION_80D_PARENTS", 40000);
            item(c, company, declaration, "HOME_LOAN_INTEREST", 180000);
            item(c, company, declaration, "SECTION_80TTA", 8000);
            item(c, company, declaration, "PROFESSIONAL_TAX", 2400);
            exec(c, "insert into payslip_components (id, company_id, name, kind, calc, value, is_basis, sort_order) "
                    + "values (?, ?, 'House rent allowance', 'EARNING', 'PERCENT_OF_GROSS', 25, false, 1)",
                    UUID.randomUUID(), company);
            return null;
        });

        flyway("71").migrate();

        asTenant(jdbc, company, c -> {
            assertThat(one(c, "select amount::text from tax_declaration_items where declaration_id = ? and deduction = 'OTHER_123'",
                    declaration)).isEqualTo("150000.00");
            assertThat(one(c, "select amount::text from tax_declaration_items where declaration_id = ? and deduction = 'HEALTH_PARENTS_PREMIUM'",
                    declaration)).isEqualTo("40000.00");
            // A ₹40,000 claim for parents is only lawful if one is a senior, so that is what it said.
            assertThat(one(c, "select parents_senior::text from tax_declarations where id = ?", declaration)).isEqualTo("true");
            assertThat(one(c, "select interest::text from tax_house_properties where declaration_id = ?", declaration))
                    .isEqualTo("180000.00");
            assertThat(one(c, "select count(*)::text from tax_declaration_items where declaration_id = ? and deduction = 'HOME_LOAN_INTEREST'",
                    declaration)).isEqualTo("0");
            assertThat(one(c, "select deduction from tax_declaration_items where declaration_id = ? and amount = 8000",
                    declaration)).isEqualTo("SAVINGS_INTEREST");
            assertThat(one(c, "select deduction from tax_declaration_items where declaration_id = ? and amount = 2400",
                    declaration)).isEqualTo("PROFESSIONAL_TAX_OTHER");
            assertThat(one(c, "select tax_tag from payslip_components where company_id = ?", company)).isEqualTo("HRA");
            return null;
        });
    }

    // ---- helpers ----

    private static void item(Connection c, UUID company, UUID declaration, String key, long amount) throws java.sql.SQLException {
        exec(c, "insert into tax_declaration_items (id, company_id, declaration_id, deduction, amount) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), company, declaration, key, amount);
    }

    private JdbcTemplate freshSchemaAt70() {
        flyway("70").clean();
        flyway("70").migrate();
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
