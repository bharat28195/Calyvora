package com.calyvora.common.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs a piece of work against a named tenant, inside a transaction that is already open.
 *
 * <p>Exists because of a trap that has now caused two production-only failures in this codebase, and
 * which is invisible in tests because the test database connects as a superuser and RLS is therefore
 * inert.
 *
 * <p>The trap: {@code TenantAwareDataSource} binds {@code calyvora.company_id} when a connection is
 * <em>borrowed</em>. A {@code @Transactional} method borrows its connection on entry. So setting
 * {@link TenantContext} inside such a method changes nothing at all — the connection in hand was
 * already bound, to whatever was current at entry, which on a public endpoint is nothing. Every write
 * to an RLS-protected table then fails, or worse, every read quietly returns no rows and a delete
 * quietly removes nothing while reporting success.
 *
 * <p>So this sets the GUC on the connection that is actually in use, flushes while it is still set —
 * a deferred Hibernate insert would otherwise land after the restore and be refused — and puts back
 * what was there before, because the caller's own tenant must survive the excursion.
 *
 * <p>This is not a way around Row-Level Security. It is how RLS expects to be told which tenant a
 * write belongs to, for the handful of cases where that tenant is known but is not the request's own:
 * a public invitation-accept that must create a row in the inviting company, or a platform owner
 * creating the first admin of a customer's workspace.
 */
@Component
public class TenantBinder {

    @PersistenceContext
    private EntityManager entityManager;

    /** Run {@code work} as {@code companyId}, restoring the previous tenant afterwards. */
    public <T> T callAs(UUID companyId, Supplier<T> work) {
        UUID previous = TenantContext.getCompanyIdOrNull();
        bind(companyId);
        TenantContext.setCompanyId(companyId);
        try {
            T result = work.get();
            // While the tenant is still bound. Hibernate would otherwise defer these inserts to the
            // end of the transaction, by which point the connection is back on the caller's tenant
            // and the policy refuses them.
            entityManager.flush();
            return result;
        } finally {
            bind(previous);
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.setCompanyId(previous);
            }
        }
    }

    /** Session-scoped, matching {@code TenantAwareDataSource}; empty means "no tenant", which denies. */
    private void bind(UUID companyId) {
        entityManager
                .createNativeQuery("select set_config('calyvora.company_id', :companyId, false)")
                .setParameter("companyId", companyId == null ? "" : companyId.toString())
                .getSingleResult();
    }
}
