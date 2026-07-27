package com.calyvora.identity;

/**
 * Company role ladder (PD-10). OWNER is the platform vendor (above all companies); ADMIN runs a
 * company; HR handles people-ops (people, payroll, leave, recruiting); MANAGER leads a team; MEMBER is
 * self-service only. Access is enforced by {@code @PreAuthorize} on controllers.
 */
public enum Role {
    OWNER,
    ADMIN,
    HR,
    MANAGER,
    MEMBER
}
