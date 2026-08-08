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
}
