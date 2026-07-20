package com.calyvora.common.config;

import com.calyvora.common.security.TenantContext;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Binds the request's tenant to every pooled connection as the Postgres session GUC
 * {@code calyvora.company_id}, which the RLS policies (V12, SD-2) read. Set on borrow from
 * {@link TenantContext}; because every borrow re-sets it (to the current tenant, or empty when
 * none is bound), a connection returned to the pool can never leak one tenant's id into the next
 * borrower's request. An empty value makes the RLS predicate NULL — deny-by-default.
 *
 * <p>This is the enforcement wiring; it only bites when the app connects as a NOSUPERUSER role
 * (superusers bypass RLS). See V12 for that requirement.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
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
        // set_config(..., is_local=false) → session scope; parameterized, so a tenant id can
        // never be an injection vector. Empty string when unbound → NULL in the RLS predicate.
        try (PreparedStatement ps =
                     connection.prepareStatement("select set_config('calyvora.company_id', ?, false)")) {
            ps.setString(1, tenant == null ? "" : tenant.toString());
            ps.execute();
        } catch (SQLException e) {
            // Never hand back a connection we couldn't scope — that would silently disable isolation.
            connection.close();
            throw e;
        }
        return connection;
    }
}
