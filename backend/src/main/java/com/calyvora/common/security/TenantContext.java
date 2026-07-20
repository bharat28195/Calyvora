package com.calyvora.common.security;

import java.util.UUID;

/**
 * Ambient, per-request tenant identity (SD-2). Set by {@link TenantFilter} immediately after
 * authentication and cleared at the end of the request. Every tenant-scoped query must filter on
 * {@link #getCompanyId()}; services reject any resource whose {@code company_id} differs.
 *
 * <p>Backed by a {@link ThreadLocal}. Sprint 1 is a synchronous request-per-thread model; when we
 * introduce reactive or async flows we must propagate this explicitly (noted as tech debt).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_COMPANY = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCompanyId(UUID companyId) {
        CURRENT_COMPANY.set(companyId);
    }

    /** @return the current tenant, or {@code null} for unauthenticated/public requests. */
    public static UUID getCompanyIdOrNull() {
        return CURRENT_COMPANY.get();
    }

    /**
     * @return the current tenant.
     * @throws IllegalStateException if no tenant is bound — a programming error on a tenant-scoped path.
     */
    public static UUID getCompanyId() {
        UUID id = CURRENT_COMPANY.get();
        if (id == null) {
            throw new IllegalStateException("No tenant bound to the current request");
        }
        return id;
    }

    public static boolean isSet() {
        return CURRENT_COMPANY.get() != null;
    }

    public static void clear() {
        CURRENT_COMPANY.remove();
    }
}
