package com.calyvora.support;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Imports {@link RlsRoleDataSource} into a test's context, so that test runs as a role Row-Level
 * Security applies to.
 *
 * <p>A test that manages roles or databases ITSELF must opt out with
 * {@code @TestPropertySource(properties = "calyvora.test.rls-role=false")}: creating a database or a
 * role needs superuser, and a test that arranges its fixture on a plain connection before dropping
 * privileges deliberately cannot have them dropped underneath it. Those are the only two exemptions,
 * and both are tests ABOUT Row-Level Security rather than tests that merely run under it.
 *
 * <p>Wraps outside {@code TenantAwareDataSource} — which is what {@code TenantDataSourceWrapper}
 * produces — so a borrowed connection gets its tenant GUC set and then drops to the restricted role.
 * Order matters only in that both must happen on the same connection before it is handed over; the
 * GUC survives {@code SET ROLE}.
 */
@TestConfiguration
public class RlsRoleConfig {

    /** Opt out with {@code calyvora.test.rls-role=false} — see {@link #OPT_OUT}. */
    public static final String OPT_OUT = "calyvora.test.rls-role";

    @Bean
    @ConditionalOnProperty(name = OPT_OUT, havingValue = "true", matchIfMissing = true)
    public static BeanPostProcessor rlsRoleWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource ds && !(bean instanceof RlsRoleDataSource)) {
                    return new RlsRoleDataSource(ds);
                }
                return bean;
            }
        };
    }

}
