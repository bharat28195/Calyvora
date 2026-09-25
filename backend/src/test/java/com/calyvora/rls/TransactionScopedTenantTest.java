package com.calyvora.rls;

import com.calyvora.support.IntegrationTestBase;
import com.calyvora.support.RlsRoleConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant bindings that do not outlive their transaction.
 *
 * <p>This is the whole of 4.2, stated as a property. A transaction pooler — PgBouncer in transaction
 * mode, and every managed equivalent — lends one physical connection for the length of a transaction
 * and then lends it to somebody else. Anything left in session state is inherited by the next client,
 * and under session-scoped binding the thing left behind is <em>the tenant id</em>. The next
 * transaction then runs somebody else's predicate, reads somebody else's rows, and succeeds.
 *
 * <p>There is no pooler in this test, and there does not need to be one: what a pooler requires is
 * that nothing survives the transaction, and that is a property of our own connection handling that
 * can be asserted directly. A test that stood up PgBouncer would be testing PgBouncer.
 *
 * <p>Run in transaction scope, which is not the default. The default remains session scope because
 * that is what the single-instance deployment is; this class is what makes turning it on a decision
 * rather than a leap.
 */
@Import(RlsRoleConfig.class)
@TestPropertySource(properties = "calyvora.rls.scope=transaction")
class TransactionScopedTenantTest extends IntegrationTestBase {

    private static final String PW = "Passw0rd!x";

    @Autowired
    private DataSource dataSource;

    /** What the GUC says on a freshly borrowed connection, outside any transaction. */
    private String bindingOutsideTransaction() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select coalesce(current_setting('calyvora.company_id', true), '')")) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    @Test
    @DisplayName("nothing is left on the connection for the next borrower to inherit")
    void a_binding_does_not_outlive_its_transaction() throws Exception {
        // A request that reads plenty, inside transactions, as the real thing does.
        Session owner = onboardOwner("Pooled Ltd", "owner@pooled.test", PW);
        assertThat(getJson("/api/v1/people/employees", owner).size()).isEqualTo(1);

        // The assertion a transaction pooler depends on. If this came back with a company id, that
        // id is what the next client on this connection would silently be scoped to.
        assertThat(bindingOutsideTransaction())
                .as("a tenant id surviving here is a cross-tenant read waiting for the next borrower")
                .isEmpty();
    }

    @Test
    @DisplayName("the app still works when bindings are transaction-scoped")
    void the_application_is_unchanged_by_the_scope() throws Exception {
        // The point of shipping this behind a flag is that the flag changes isolation and nothing
        // else. Two tenants, each seeing exactly their own, under the stricter contract.
        Session acme = onboardOwner("Pooled Acme", "owner@p-acme.test", PW);
        Session other = onboardOwner("Pooled Umbrella", "owner@p-umbrella.test", PW);

        JsonNode acmeDir = getJson("/api/v1/people/employees", acme);
        assertThat(acmeDir.findValuesAsText("email")).containsExactly("owner@p-acme.test");

        JsonNode otherDir = getJson("/api/v1/people/employees", other);
        assertThat(otherDir.findValuesAsText("email")).containsExactly("owner@p-umbrella.test");
    }

    @Test
    @DisplayName("outside a transaction nothing is bound, so a tenant read finds nothing")
    void outside_a_transaction_the_answer_is_none_rather_than_somebody_elses() throws Exception {
        onboardOwner("Pooled Three", "owner@p-three.test", PW);

        // Deny by default, stated. Under session scope an unbound connection also reads nothing —
        // but only because the previous borrow happened to clear it. Here it is the contract: a
        // tenant-scoped read outside a transaction has no tenant and therefore no rows.
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from employees")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1))
                    .as("an unbound read must return nothing, never another tenant's rows")
                    .isZero();
        }
    }
}
