package com.calyvora.support;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes the application connect as a role that Row-Level Security actually applies to.
 *
 * <p>This exists because the test database is the reason two production-only outages got through a
 * green suite. Zonky connects as {@code postgres}, and <b>a superuser bypasses RLS entirely</b> — so
 * every policy in V12 is inert under test. V45 created a policy before its own backfill and blocked
 * every deploy for weeks while 400-odd tests stayed green; the scale seeder repeated the same mistake
 * from the other direction; and V30 switched a table's isolation off rather than solve it. None of
 * those could have been caught here, because here there was nothing to catch.
 *
 * <p>Rather than authenticate as a second role — the embedded server's auth config is not ours to
 * depend on — this drops privileges with {@code SET ROLE} on every borrowed connection. What matters
 * is not which credentials were presented but which role the session runs as, and that is what RLS
 * reads.
 *
 * <p>The role is granted enough to run the migrations too, so the schema is created and owned by it,
 * exactly as in production. That matters more than it looks: {@code FORCE ROW LEVEL SECURITY} exists
 * precisely because a table's owner is otherwise exempt from its own policies.
 */
public class RlsRoleDataSource extends DelegatingDataSource {

    /**
     * Distinct from the role RlsTenantIsolationTest creates and drops. That test asserts the policy
     * itself against a throwaway role; this one runs the whole application as a lasting one, and a
     * shared name meant its DROP ROLE failed on objects this class had been granted.
     */
    public static final String ROLE = "calyvora_rls_runtime";

    /** Zonky hands out a fresh database per test method; grants are per-database, so track by name. */
    private static final Map<String, Boolean> PREPARED = new ConcurrentHashMap<>();

    public RlsRoleDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dropPrivileges(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return dropPrivileges(super.getConnection(username, password));
    }

    private Connection dropPrivileges(Connection connection) throws SQLException {
        try {
            String catalog = connection.getCatalog();
            if (PREPARED.putIfAbsent(catalog, Boolean.TRUE) == null) {
                prepare(connection);
            }
            try (Statement st = connection.createStatement()) {
                st.execute("set role " + ROLE);
            }
        } catch (SQLException e) {
            // Never hand back a connection still holding superuser: it would silently restore the
            // very blind spot this class exists to remove, and the suite would go green for the
            // wrong reason.
            connection.close();
            throw e;
        }
        return connection;
    }

    private void prepare(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            // Cluster-wide and shared between test databases, so create it only once. NOSUPERUSER and
            // NOBYPASSRLS are the entire point of the class.
            st.execute("""
                    do $$
                    begin
                        if not exists (select 1 from pg_roles where rolname = '%s') then
                            create role %s nosuperuser nobypassrls;
                        end if;
                    end $$
                    """.formatted(ROLE, ROLE));
            // Installing an extension needs superuser here, and needs it in production too — where a
            // managed Postgres already has pgcrypto available. So it is created before privileges are
            // dropped, and V1's "if not exists" then finds it. That mirrors the real deployment
            // rather than granting the app a right it does not hold there.
            st.execute("create extension if not exists \"pgcrypto\"");
            st.execute("grant all on schema public to " + ROLE);
            st.execute("grant all on all tables in schema public to " + ROLE);
            st.execute("grant all on all sequences in schema public to " + ROLE);
            // Flyway runs as this role too, so tables it is about to create must also be reachable.
            st.execute("alter default privileges in schema public grant all on tables to " + ROLE);
            st.execute("alter default privileges in schema public grant all on sequences to " + ROLE);
        }
    }
}
