package com.calyvora.identity;

/**
 * Role ladder (PD-10, extended by PD-18). Two of these sit above a single company:
 *
 * <ul>
 *   <li>{@code OWNER} — the platform vendor, above every company and agency. Only this role starts and
 *       ends subscriptions.</li>
 *   <li>{@code AGENCY_OWNER} — a customer running several companies. Sees only its own companies, and
 *       only their summaries; it can create companies and ask for seats, never activate billing.</li>
 * </ul>
 *
 * The rest are within one company: {@code ADMIN} runs it, {@code HR} handles people-ops,
 * {@code MANAGER} leads a team, {@code MEMBER} is self-service only.
 *
 * <p>Access is enforced by {@code @PreAuthorize} on controllers, and for the two console roles by a
 * second check that the caller belongs to the platform/agency company — see {@code PlatformAccess}
 * and {@code AgencyAccess}. The role alone is never enough.
 */
public enum Role {
    OWNER,
    AGENCY_OWNER,
    ADMIN,
    HR,
    MANAGER,
    MEMBER
}
