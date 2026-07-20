package com.calyvora.email;

/**
 * Transactional email. Sprint 1 has a local SMTP impl (Mailpit); prod swaps to SES/Resend (SD-6).
 * Links passed in are already fully-formed absolute URLs to the frontend.
 */
public interface EmailService {

    void sendVerificationEmail(String to, String verificationUrl);

    void sendInvitationEmail(String to, String companyName, String acceptUrl);
}
