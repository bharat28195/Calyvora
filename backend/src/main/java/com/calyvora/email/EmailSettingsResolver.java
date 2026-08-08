package com.calyvora.email;

import java.util.UUID;

/**
 * Decides which mailbox a given company's outgoing mail is sent from.
 *
 * <p>This is the seam for "bring your own mailbox": today {@link PlatformEmailSettingsResolver}
 * returns the platform's own settings for every tenant, so all mail goes out from e.g.
 * {@code no-reply@calyvora.in}. When per-tenant sending lands, a resolver that reads the company's
 * saved credentials and falls back to the platform default slots in here — no sender, and no caller,
 * needs to change.
 */
public interface EmailSettingsResolver {

    /**
     * @param companyId the tenant the mail belongs to, or {@code null} when there isn't one yet
     *                  (a diagnostic send, or a signup before its company is resolved).
     */
    EmailSettings resolve(UUID companyId);
}
