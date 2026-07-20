package com.calyvora.common.security;

import java.util.UUID;

/**
 * The authenticated caller, derived entirely from a verified access-token JWT. Immutable and
 * request-scoped. {@code role} is the bare role name (e.g. {@code OWNER}); Spring authorities are
 * the {@code ROLE_}-prefixed form.
 */
public record AuthPrincipal(
        UUID userId,
        UUID companyId,
        String role,
        String email
) {
}
