package com.calyvora.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Startup guard for multi-tenant safety (SD-2). Row-Level Security is the database-level backstop
 * that stops one tenant reading another's rows — but Postgres SUPERUSER and BYPASSRLS roles ignore
 * RLS entirely, which would make that backstop inert. Managed Postgres providers differ: Render,
 * Neon and Supabase hand you a non-superuser role (safe); a raw Railway/Docker Postgres hands you
 * the {@code postgres} superuser (unsafe) unless you create a dedicated app role.
 *
 * <p>So in any hosted profile we check the role we actually connected as and, if it can bypass RLS,
 * refuse to start with an actionable message rather than silently leaking tenant data. Set
 * {@code REQUIRE_TENANT_ISOLATION=false} only if you fully understand the consequences.
 */
@Component
@Profile({"staging", "prod"})
public class TenantIsolationVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolationVerifier.class);

    private final DataSource dataSource;
    private final boolean required;

    public TenantIsolationVerifier(DataSource dataSource,
                                   @Value("${calyvora.security.require-tenant-isolation:true}") boolean required) {
        this.dataSource = dataSource;
        this.required = required;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select current_user, rolsuper, rolbypassrls " +
                     "from pg_roles where rolname = current_user")) {
            if (!rs.next()) return; // can't determine — don't block startup
            String role = rs.getString("current_user");
            boolean superuser = rs.getBoolean("rolsuper");
            boolean bypassRls = rs.getBoolean("rolbypassrls");

            if (superuser || bypassRls) {
                String why = superuser ? "is a SUPERUSER" : "has the BYPASSRLS attribute";
                String message = String.format(
                        "TENANT ISOLATION IS UNSAFE: the database role '%s' %s, so it BYPASSES "
                        + "Row-Level Security and one tenant could read another tenant's data. "
                        + "Fix: connect as a NOSUPERUSER role without BYPASSRLS (Render/Neon/Supabase "
                        + "give you one by default; on Railway/self-managed Postgres create a dedicated "
                        + "app role — see DEPLOY.md). To override intentionally set "
                        + "REQUIRE_TENANT_ISOLATION=false.", role, why);
                if (required) {
                    throw new IllegalStateException(message);
                }
                log.error("[TENANT ISOLATION] {}", message);
            } else {
                log.info("[TENANT ISOLATION] OK — role '{}' is NOSUPERUSER without BYPASSRLS; "
                        + "Row-Level Security is enforced.", role);
            }
        }
    }
}
