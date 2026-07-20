package com.calyvora.common.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Wraps whatever {@link DataSource} the context builds (Hikari in prod, the embedded Postgres in
 * dev/test) in a {@link TenantAwareDataSource}, so the RLS session GUC is set on every borrowed
 * connection (SD-2). Doing this as a {@link BeanPostProcessor} keeps it agnostic to how the
 * DataSource is provided — including when a test harness supplies its own.
 */
@Component
public class TenantDataSourceWrapper implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(ds);
        }
        return bean;
    }
}
