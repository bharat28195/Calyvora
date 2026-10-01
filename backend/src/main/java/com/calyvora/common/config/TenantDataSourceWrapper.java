package com.calyvora.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Locale;

/**
 * Wraps whatever {@link DataSource} the context builds (Hikari in prod, the embedded Postgres in
 * dev/test) in a {@link TenantAwareDataSource}, so the RLS GUC is set on every borrowed connection
 * (SD-2). Doing this as a {@link BeanPostProcessor} keeps it agnostic to how the DataSource is
 * provided — including when a test harness supplies its own.
 *
 * <p>{@code calyvora.rls.scope} chooses how long a binding lives: {@code session} (the default, and
 * what a single instance owning its own pool wants) or {@code transaction} (what a deployment behind
 * a transaction pooler requires). The choice is logged at startup, because "which isolation mode is
 * this running in" is not a question anybody should answer by reading a config file during an
 * incident.
 */
@Component
public class TenantDataSourceWrapper implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceWrapper.class);

    private final TenantAwareDataSource.Scope scope;
    private boolean announced;

    public TenantDataSourceWrapper(@Value("${calyvora.rls.scope:session}") String configured) {
        this.scope = parse(configured);
    }

    private static TenantAwareDataSource.Scope parse(String configured) {
        String value = configured == null ? "" : configured.trim().toUpperCase(Locale.ROOT);
        try {
            return TenantAwareDataSource.Scope.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            // Fall back to the safe-for-today default rather than refusing to start — but say so
            // loudly. Running in the wrong isolation mode without anybody knowing is the failure
            // worth avoiding here; a startup that dies on a typo is merely annoying.
            log.error("calyvora.rls.scope='{}' is neither 'session' nor 'transaction'; using session.",
                    configured);
            return TenantAwareDataSource.Scope.SESSION;
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)) {
            if (!announced) {
                announced = true;
                log.info("Tenant isolation: {} scope.{}", scope,
                        scope == TenantAwareDataSource.Scope.TRANSACTION
                                ? " A binding does not outlive its transaction, which is what a"
                                        + " transaction pooler requires."
                                : " A binding lives on the connection; safe while this process owns"
                                        + " its pool, unsafe behind a transaction pooler.");
            }
            return new TenantAwareDataSource(ds, scope);
        }
        return bean;
    }
}
