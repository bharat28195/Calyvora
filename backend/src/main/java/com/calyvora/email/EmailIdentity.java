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

    /**
     * {@code Northwind Robotics via Orbit <no-reply@…>} for mail sent on a company's behalf — a letter
     * from your employer should say who it is from, and "via Orbit" says honestly how it came.
     */
    static String from(EmailSettings settings, String onBehalfOf) {
        return displayName(onBehalfOf) + " <" + settings.from() + ">";
    }

    static String displayName(String onBehalfOf) {
        if (onBehalfOf == null || onBehalfOf.isBlank()) return DISPLAY_NAME;
        // Quotes, angle brackets and line breaks would break the header; nothing legitimate needs them.
        String clean = onBehalfOf.replaceAll("[\"<>\r\n]", "").trim();
        return clean.isEmpty() ? DISPLAY_NAME : clean + " via " + DISPLAY_NAME;
    }
}
