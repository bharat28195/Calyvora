package com.calyvora.support;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Imports {@link RlsRoleDataSource} into a test's context, so that test runs as a role Row-Level
 * Security applies to.
 *
 * <p>Wraps outside {@code TenantAwareDataSource} — which is what {@code TenantDataSourceWrapper}
 * produces — so a borrowed connection gets its tenant GUC set and then drops to the restricted role.
 * Order matters only in that both must happen on the same connection before it is handed over; the
 * GUC survives {@code SET ROLE}.
 */
@TestConfiguration
public class RlsRoleConfig {

    @Bean
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
