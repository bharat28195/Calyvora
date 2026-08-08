package com.calyvora.email;

/**
 * The wording of every transactional email, in one place. Kept apart from the senders so the copy
 * can't drift between transports — the text a customer receives must not depend on whether the
 * deployment happens to be on Resend or SMTP.
 */
final class EmailMessages {

    private EmailMessages() {}

    record Message(String subject, String body) {}

    static Message verification(String verificationUrl) {
        return new Message("Verify your Calyvora email",
                "Welcome to Calyvora!\n\nConfirm your email address to activate your account:\n"
                        + verificationUrl + "\n\nThis link expires in 24 hours.");
    }

    static Message invitation(String companyName, String acceptUrl) {
        return new Message("You're invited to join " + companyName + " on Calyvora",
                "You've been invited to join " + companyName + " on Calyvora.\n\n"
                        + "Accept your invitation and set a password:\n" + acceptUrl
                        + "\n\nThis invitation expires in 7 days.");
    }
}
