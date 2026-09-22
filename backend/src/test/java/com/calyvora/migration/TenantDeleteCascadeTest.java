package com.calyvora.migration;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every table that belongs to a tenant can be deleted with the tenant.
 *
 * <p>This is the test, not the migration. V58 made forty-six foreign keys cascade, which fixes the
 * schema as it stands today; what actually keeps a tenant delete complete is this assertion running
 * against whatever the schema becomes. A table added next month with a {@code company_id} and no
 * cascading key would leave its rows behind on an erasure request — the delete would report success,
 * the customer's personal data would still be there, and nothing would say so.
 *
 * <p>Reads the live catalogue rather than a list kept here, for the same reason: a list in a test is
 * one more thing to forget to update, and it would agree with the code that forgot.
 *
 * <p>Runs without the restricted role: reading {@code pg_catalog} and deleting a whole company are
 * both things the policy layer is not meant to permit, and this class is about the schema rather
 * than about isolation.
 */
@TestPropertySource(properties = "calyvora.test.rls-role=false")
class TenantDeleteCascadeTest extends IntegrationTestBase {

    @Autowired
    private DataSource dataSource;

    /** Tables carrying a company_id, straight from the catalogue. */
    private List<String> tablesWithCompanyId(Connection c) throws Exception {
        List<String> names = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select c.relname from pg_attribute a "
                             + "join pg_class c on c.oid = a.attrelid "
                             + "join pg_namespace n on n.oid = c.relnamespace "
                             + "where a.attname = 'company_id' and c.relkind = 'r' "
                             + "and n.nspname = current_schema() and a.attnum > 0 "
                             + "order by c.relname")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    @Test
    @DisplayName("every table with a company_id cascades from companies")
    void nothing_survives_its_tenant() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            List<String> tables = tablesWithCompanyId(c);
            assertThat(tables)
                    .as("the catalogue query must actually find the tenant tables")
                    .hasSizeGreaterThan(30);

            List<String> offenders = new ArrayList<>();
            for (String table : tables) {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery(
                             "select con.confdeltype from pg_constraint con "
                                     + "join pg_class rel on rel.oid = con.conrelid "
                                     + "join pg_class ref on ref.oid = con.confrelid "
                                     + "where con.contype = 'f' and ref.relname = 'companies' "
                                     + "and rel.relname = '" + table + "' "
                                     + "and (select attname from pg_attribute "
                                     + "     where attrelid = con.conrelid and attnum = con.conkey[1]) "
                                     + "    = 'company_id'")) {
                    if (!rs.next()) {
                        offenders.add(table + " (no foreign key to companies at all)");
                    } else if (!"c".equals(rs.getString(1))) {
                        offenders.add(table + " (foreign key does not cascade)");
                    }
                }
            }

            assertThat(offenders)
                    .as("these tables would outlive the tenant they belong to:%n%s",
                            String.join("\n", offenders))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("deleting an agency leaves its customers standing")
    void an_agency_does_not_take_its_customers_with_it() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select con.confdeltype from pg_constraint con "
                             + "join pg_class rel on rel.oid = con.conrelid "
                             + "where con.contype = 'f' and rel.relname = 'companies' "
                             + "and (select attname from pg_attribute "
                             + "     where attrelid = con.conrelid and attnum = con.conkey[1]) = 'agency_id'")) {
            assertThat(rs.next()).as("companies.agency_id must have a foreign key").isTrue();
            // 'n' = SET NULL. Cascading here would turn "remove this reseller" into "remove every
            // company that reseller ever signed" — the most destructive thing this schema could be
            // asked to do by accident.
            assertThat(rs.getString(1))
                    .as("an agency's customers are tenants in their own right and must survive it")
                    .isEqualTo("n");
        }
    }
}
