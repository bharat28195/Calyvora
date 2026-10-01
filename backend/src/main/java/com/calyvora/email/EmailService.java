package com.calyvora.email;

/**
 * Transactional email. Links passed in are already fully-formed absolute URLs to the frontend.
 *
 * <p>Sends return an {@link EmailResult} rather than throwing: a mail failure must not roll back the
 * registration or invitation that triggered it, but the caller still needs to know whether anything
 * was delivered so it can offer a resend instead of claiming success.
 */
public interface EmailService {

    EmailResult sendVerificationEmail(String to, String verificationUrl);

    EmailResult sendInvitationEmail(String to, String companyName, String acceptUrl);

    /** A one-time code for setting a new password (PD-23). */
    EmailResult sendPasswordResetCode(String to, String code, long expiresInMinutes);

    /** To the vendor: a new trial enquiry is waiting for a decision (PD-21). */
    EmailResult sendTrialRequestNotification(String to, TrialEnquiry enquiry);

    /**
     * To the person who asked: we have it, a human will come back to you. Sent because the alternative
     * — a form that silently succeeds — is indistinguishable from one that is broken, and someone who
     * thinks nothing happened will simply submit again.
     */
    EmailResult sendTrialRequestAcknowledgement(String to, String contactName);

    /** To the customer, once the vendor approves: the workspace exists and here is where to sign in. */
    EmailResult sendTrialApprovedEmail(String to, String companyName, String loginUrl);
}
