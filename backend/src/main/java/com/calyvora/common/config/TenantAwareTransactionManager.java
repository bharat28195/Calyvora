package com.calyvora.common.config;

import com.calyvora.common.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Names the tenant at the start of every transaction, for deployments that bind transaction-locally.
 *
 * <p>This exists because the obvious place to do it does not work. {@link TenantAwareDataSource}
 * binds when a connection is borrowed, and the natural thought is to make that binding
 * {@code is_local = true} whenever a transaction is open. It never is: Spring obtains the
 * connection inside {@code doBegin} and only marks the transaction active <em>afterwards</em>, in
 * {@code prepareSynchronization}. So at borrow time {@code isActualTransactionActive()} is false,
 * every binding is made session-scoped, and transaction scope quietly degrades into the thing it was
 * meant to replace — which is the worst possible outcome, because the configuration says one thing
 * and the database does another.
 *
 * <p>So the binding is made here, immediately after {@code super.doBegin} has opened the transaction
 * and bound the EntityManager to the thread. At that point {@code set_config(..., true)} means what
 * it says: the value applies to this transaction and Postgres discards it at commit or rollback,
 * leaving nothing for the next client of a pooled connection to inherit.
 *
 * <p>Only active in {@link TenantAwareDataSource.Scope#TRANSACTION}. Under session scope this is an
 * ordinary {@link JpaTransactionManager} and the DataSource does the binding as it always has.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    private final TenantAwareDataSource.Scope scope;

    public TenantAwareTransactionManager(EntityManagerFactory emf, TenantAwareDataSource.Scope scope) {
        super(emf);
        this.scope = scope;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        if (scope != TenantAwareDataSource.Scope.TRANSACTION) {
            return;
        }
        UUID tenant = TenantContext.getCompanyIdOrNull();
        if (tenant == null) {
            // Nothing to bind. The connection was handed over with an empty session value, so the
            // policy denies rather than inheriting — which is the point.
            return;
        }
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(getEntityManagerFactory());
        if (holder == null) {
            return;
        }
        EntityManager em = holder.getEntityManager();
        em.createNativeQuery("select set_config('calyvora.company_id', :companyId, true)")
                .setParameter("companyId", tenant.toString())
                .getSingleResult();
    }
}
