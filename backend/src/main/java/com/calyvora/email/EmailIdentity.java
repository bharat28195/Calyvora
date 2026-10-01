package com.calyvora.email;

/**
 * Who the mail appears to come from.
 *
 * <p>Shared by the transports so the two cannot disagree — an inbox showing "Orbit" on Resend and a
 * bare address on SMTP would be the same product introducing itself two different ways.
 *
 * <p>The display name is the product, not the company. A recipient recognises the thing they signed
 * in to; "Calyvora" would be a name most of them have never seen, and an unrecognised sender on a
 * password-reset mail is deleted as phishing.
 */
final class EmailIdentity {

    private EmailIdentity() {}

    static final String DISPLAY_NAME = "Orbit";

    /** {@code Orbit <no-reply@calyvora.in>} — the form both a header and Resend's API accept. */
    static String from(EmailSettings settings) {
        return DISPLAY_NAME + " <" + settings.from() + ">";
    }
}
