package com.calyvora.common.config;

import com.calyvora.common.security.TenantContext;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Binds the request's tenant to the Postgres GUC {@code calyvora.company_id}, which the RLS policies
 * (V12, SD-2) read.
 *
 * <p>Set on borrow from {@link TenantContext}. Because every borrow re-sets it — to the current
 * tenant, or empty when none is bound — a connection returned to the pool can never leak one
 * tenant's id into the next borrower's request. An empty value makes the RLS predicate NULL, which
 * is deny-by-default.
 *
 * <p>This is the enforcement wiring; it only bites when the app connects as a NOSUPERUSER role
 * (superusers bypass RLS). See V12 for that requirement.
 *
 * <h2>Session scope, and why it is not the end of the story</h2>
 *
 * <p>{@code set_config(..., is_local = false)} is <em>session</em> scope: the value stays on the
 * connection until something changes it. That is exactly right while the application owns its
 * connections — one pool, one process, every borrow re-set — and it is wrong the moment a
 * <b>transaction pooler</b> is put in front of Postgres.
 *
 * <p>A transaction pooler (PgBouncer in transaction mode, and every managed equivalent) is how one
 * database serves many application instances: it lends a physical connection for the length of a
 * transaction and then lends it to somebody else. Session state left by the first client is still
 * there for the second. Under session-scoped binding that state is <em>the tenant id</em>, and the
 * failure is not an error — it is tenant B running tenant A's predicate, reading tenant A's rows,
 * and reporting success. A silent cross-tenant read is the worst thing this system can do.
 *
 * <p>Hence {@link Scope#TRANSACTION}: the value is set with {@code is_local = true} inside the
 * transaction that needs it, and Postgres discards it at commit or rollback. Nothing survives for
 * the next borrower to inherit, because nothing is left behind.
 *
 * <p><b>The default is still SESSION</b>, deliberately. That is what today's deployment is — one
 * instance, its own pool, nothing in front — and switching the default would change how a running
 * system behaves to suit one it does not yet have. Transaction scope also denies any tenant-scoped
 * read that happens outside a transaction, which is a stricter contract than this code has ever been
 * held to. It is built and it is tested; turning it on belongs with the decision to put a pooler
 * there.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    /** How long a tenant binding lives on a connection. */
    public enum Scope {
        /**
         * Until something changes it. Correct while the application owns its pool; unsafe behind a
         * transaction pooler, where the next borrower inherits it.
         */
        SESSION,
        /**
         * Only for the current transaction. What a transaction pooler requires — and outside a
         * transaction nothing is bound at all, so a tenant-scoped read there returns no rows rather
         * than somebody else's.
         */
        TRANSACTION
    }

    private final Scope scope;

    public TenantAwareDataSource(DataSource target) {
        this(target, Scope.SESSION);
    }

    public TenantAwareDataSource(DataSource target, Scope scope) {
        super(target);
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bindTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bindTenant(super.getConnection(username, password));
    }

    private Connection bindTenant(Connection connection) throws SQLException {
        UUID tenant = TenantContext.getCompanyIdOrNull();

        // Under transaction scope this hands the connection over deliberately empty, and
        // TenantAwareTransactionManager names the tenant once the transaction is open.
        //
        // The binding cannot be made here. Spring obtains the connection inside doBegin and marks
        // the transaction active only afterwards, so isActualTransactionActive() is always false at
        // this point — an `is_local` set here would silently become session-scoped, and transaction
        // scope would degrade into exactly the behaviour it exists to replace while the
        // configuration claimed otherwise.
        //
        // Clearing it is not merely tidy: it is the property a transaction pooler needs. Whatever
        // the previous borrower left behind stops here, so a connection can never arrive carrying
        // somebody else's tenant.
        boolean local = false;
        String value = tenant == null || scope == Scope.TRANSACTION ? "" : tenant.toString();

        // Parameterised, so a tenant id can never be an injection vector. Empty string when unbound
        // → NULL in the RLS predicate → deny by default.
        try (PreparedStatement ps =
                     connection.prepareStatement("select set_config('calyvora.company_id', ?, ?)")) {
            ps.setString(1, value);
            ps.setBoolean(2, local);
            ps.execute();
        } catch (SQLException e) {
            // Never hand back a connection we couldn't scope — that would silently disable isolation.
            connection.close();
            throw e;
        }
        return connection;
    }
}
