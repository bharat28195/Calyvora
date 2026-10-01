package com.calyvora.identity;

public enum UserStatus {
    /** Registered as an Owner but email not yet verified. */
    PENDING_VERIFICATION,
    /** Invited employee who has not yet accepted / set a password. */
    INVITED,
    ACTIVE,
    DISABLED
}
