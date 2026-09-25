package com.calyvora.common.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Locale;

/**
 * Replaces Spring Boot's default transaction manager with one that can name the tenant at the start
 * of each transaction.
 *
 * <p>Under session scope — the default — this behaves exactly as the stock manager does, and the
 * binding continues to be made when a connection is borrowed. It matters only when
 * {@code calyvora.rls.scope=transaction}, where the binding has to happen after the transaction is
 * open; see {@link TenantAwareTransactionManager} for why nowhere earlier works.
 */
@Configuration
public class TenantTransactionConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf,
                                                         @Value("${calyvora.rls.scope:session}") String scope) {
        TenantAwareDataSource.Scope resolved;
        try {
            resolved = TenantAwareDataSource.Scope.valueOf(
                    scope == null ? "SESSION" : scope.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            resolved = TenantAwareDataSource.Scope.SESSION;
        }
        return new TenantAwareTransactionManager(emf, resolved);
    }
}
