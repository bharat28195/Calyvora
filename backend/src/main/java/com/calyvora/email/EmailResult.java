package com.calyvora.email;

/**
 * The outcome of one send. Returned rather than thrown because a mail failure must never roll back
 * the signup or invite that triggered it — but the caller still needs to know, so the UI can say
 * "we couldn't send it, try resend" instead of a confident "check your email" for a message that
 * never left the building.
 */
public record EmailResult(boolean delivered, String provider, String error) {

    public static EmailResult ok(String provider) {
        return new EmailResult(true, provider, null);
    }

    public static EmailResult failed(String provider, String error) {
        return new EmailResult(false, provider, error);
    }
}
