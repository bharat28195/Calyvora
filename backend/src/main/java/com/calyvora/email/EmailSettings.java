package com.calyvora.email;

/**
 * Everything needed to send one message: which transport, the sender identity, and that transport's
 * credentials. Resolved per send by an {@link EmailSettingsResolver}, which is the seam that lets a
 * tenant bring its own mailbox later without any sender knowing tenants exist.
 *
 * <p>Only the fields belonging to {@link Provider} are populated; the rest are ignored.
 */
public record EmailSettings(
        Provider provider,
        String from,
        /** Where a reply goes. The From address may be an unattended no-reply; this must not be. */
        String replyTo,
        /** The From used for one-time codes only — see {@link #forOneTimeCode()}. */
        String otpFrom,
        // --- HTTPS API providers (Resend) ---
        String apiKey,
        String apiUrl,
        // --- SMTP ---
        String host,
        int port,
        String username,
        String password,
        boolean auth,
        boolean starttls,
        boolean ssl
) {

    public enum Provider {
        /** Writes the message to the log instead of sending. Local development only. */
        CONSOLE,
        /** Classic SMTP. Blocked outbound by many hosts (including Render's free tier). */
        SMTP,
        /** Resend's HTTPS API — port 443, so it survives hosts that block SMTP. */
        RESEND
    }

    /** Human-readable destination, for diagnostics. Never includes the credential. */
    public String endpoint() {
        return switch (provider) {
            case CONSOLE -> "console (not sent)";
            case SMTP -> host + ":" + port;
            case RESEND -> apiUrl;
        };
    }

    public static EmailSettings console(String from) {
        return console(from, null, from);
    }

    public static EmailSettings console(String from, String replyTo, String otpFrom) {
        return new EmailSettings(Provider.CONSOLE, from, replyTo, otpFrom, null, null, null, 0, null, null, false, false, false);
    }

    public static EmailSettings resend(String from, String replyTo, String otpFrom, String apiKey, String apiUrl) {
        return new EmailSettings(Provider.RESEND, from, replyTo, otpFrom, apiKey, apiUrl, null, 0, null, null, false, false, false);
    }

    public static EmailSettings smtp(String from, String replyTo, String otpFrom, String host, int port,
                                     String username, String password, boolean auth, boolean starttls,
                                     boolean ssl) {
        return new EmailSettings(Provider.SMTP, from, replyTo, otpFrom, null, null, host, port, username,
                password, auth, starttls, ssl);
    }

    /**
     * The same transport and credentials, sending as the one-time-code address.
     *
     * <p>A copy rather than a field the senders consult, so a transport cannot accidentally apply the
     * code sender to an invitation: only the call that builds a code asks for it.
     */
    public EmailSettings forOneTimeCode() {
        String sender = otpFrom == null || otpFrom.isBlank() ? from : otpFrom;
        return new EmailSettings(provider, sender, replyTo, otpFrom, apiKey, apiUrl, host, port,
                username, password, auth, starttls, ssl);
    }
}
