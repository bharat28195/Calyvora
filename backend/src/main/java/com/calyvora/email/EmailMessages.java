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

    /**
     * The vendor's copy of a trial enquiry. The subject leads with the company name because this
     * arrives on a phone, in a list, next to everything else — "New trial request" on its own tells
     * you nothing you can act on.
     */
    static Message trialEnquiry(TrialEnquiry e) {
        StringBuilder body = new StringBuilder()
                .append(e.contactName()).append(" has asked for a free trial of Orbit.\n\n")
                .append("Company:  ").append(e.companyName()).append('\n')
                .append("Contact:  ").append(e.contactName()).append('\n')
                .append("Email:    ").append(e.email()).append('\n');
        appendIfPresent(body, "Phone:    ", e.phone());
        appendIfPresent(body, "Team:     ", e.teamSize());
        appendIfPresent(body, "Came from:", e.source());
        appendIfPresent(body, "\nWhat they said:\n", e.note());
        body.append("\nNobody can sign in until you approve this. Open the platform console to "
                + "create their workspace or turn it down:\n").append(e.consoleUrl()).append('\n');
        return new Message("Trial request: " + e.companyName() + " (" + e.contactName() + ")",
                body.toString());
    }

    /**
     * The code goes in the subject line as well as the body. Most people read it off a notification
     * without opening anything, and a subject that says only "Password reset" makes them open the
     * mail to learn six digits.
     */
    static Message passwordReset(String code, long minutes) {
        return new Message("Your Calyvora reset code is " + code,
                "Use this code to set a new password:\n\n    " + code + "\n\n"
                        + "It expires in " + minutes + " minutes and can be used once.\n\n"
                        + "If you didn't ask to reset your password, you can ignore this — nothing has "
                        + "changed, and whoever asked cannot get in without this code.");
    }

    static Message trialAcknowledgement(String contactName) {
        return new Message("We've got your Orbit trial request",
                "Hi " + contactName + ",\n\n"
                        + "Thanks for asking about a free trial of Orbit. Your request has reached us and "
                        + "someone will come back to you shortly to set your workspace up.\n\n"
                        + "There's nothing else you need to do — we'll send your sign-in details once "
                        + "your trial is ready.\n\n"
                        + "— Calyvora");
    }

    static Message trialApproved(String companyName, String loginUrl) {
        return new Message("Your Orbit trial is ready",
                "Good news — the Orbit workspace for " + companyName + " is set up and waiting.\n\n"
                        + "Sign in here:\n" + loginUrl + "\n\n"
                        + "Your sign-in details are coming separately from the person you've been "
                        + "speaking to.\n\n"
                        + "— Calyvora");
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(' ').append(value).append('\n');
        }
    }
}
