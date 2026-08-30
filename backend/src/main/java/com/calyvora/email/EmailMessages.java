package com.calyvora.email;

import static com.calyvora.email.EmailLayout.escape;

/**
 * The wording of every transactional email, in one place. Kept apart from the senders so the copy
 * can't drift between transports — the text a customer receives must not depend on whether the
 * deployment happens to be on Resend or SMTP.
 *
 * <p>Every message carries both a plain-text and an HTML body, sent together as alternatives. The
 * text part is not a legacy courtesy: some clients are configured to show it, spam filters read it,
 * and a message with no text part scores worse for it. The two must always say the same thing.
 *
 * <p>The product is <strong>Orbit</strong> and the company is Calyvora. Mail talks about the thing
 * the reader signed in to, so it says Orbit; Calyvora appears once, in the footer, to explain who
 * Orbit is from.
 */
final class EmailMessages {

    private EmailMessages() {}

    record Message(String subject, String body, String html) {}

    static Message verification(String verificationUrl) {
        String text = """
                Welcome to Orbit.

                Confirm your email address to activate your account:
                %s

                This link expires in 24 hours.""".formatted(verificationUrl);

        String html = EmailLayout.page("Confirm your email address to activate your Orbit account.",
                EmailLayout.heading("Confirm your email address")
                        + EmailLayout.paragraph("Welcome to Orbit. One step and your account is active.")
                        + EmailLayout.button("Confirm my email", verificationUrl)
                        + EmailLayout.fallbackLink(verificationUrl)
                        + EmailLayout.muted("This link expires in 24 hours."));

        return new Message("Confirm your email address for Orbit", text, html);
    }

    static Message invitation(String companyName, String acceptUrl) {
        String text = """
                You've been invited to join %s on Orbit.

                Accept your invitation and set a password:
                %s

                This invitation expires in 7 days.""".formatted(companyName, acceptUrl);

        String html = EmailLayout.page("You've been invited to join " + companyName + " on Orbit.",
                EmailLayout.heading("You've been invited to " + companyName)
                        + EmailLayout.paragraph("You've been invited to join <strong>"
                                + escape(companyName) + "</strong> on Orbit. Accept below and choose a password.")
                        + EmailLayout.button("Accept the invitation", acceptUrl)
                        + EmailLayout.fallbackLink(acceptUrl)
                        + EmailLayout.muted("This invitation expires in 7 days."));

        return new Message("You're invited to join " + companyName + " on Orbit", text, html);
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

        StringBuilder rows = new StringBuilder()
                .append(EmailLayout.field("Company", e.companyName()))
                .append(EmailLayout.field("Contact", e.contactName()))
                .append(EmailLayout.field("Email", e.email()));
        fieldIfPresent(rows, "Phone", e.phone());
        fieldIfPresent(rows, "Team size", e.teamSize());
        fieldIfPresent(rows, "Came from", e.source());

        String html = EmailLayout.page(e.companyName() + " asked for a free trial of Orbit.",
                EmailLayout.heading("New trial request")
                        + EmailLayout.paragraph("<strong>" + escape(e.contactName())
                                + "</strong> has asked for a free trial of Orbit.")
                        + EmailLayout.fields(rows.toString())
                        + (e.note() == null || e.note().isBlank() ? ""
                                : EmailLayout.paragraph("&ldquo;" + escape(e.note()) + "&rdquo;"))
                        + EmailLayout.paragraph("Nobody can sign in until you approve this.")
                        + EmailLayout.button("Open the console", e.consoleUrl()));

        return new Message("Trial request: " + e.companyName() + " (" + e.contactName() + ")",
                body.toString(), html);
    }

    /**
     * The code is deliberately NOT in the subject line.
     *
     * <p>It used to be, on the reasoning that people read a code off a notification without opening
     * anything. That convenience is real, but it is bought by printing the credential somewhere it
     * cannot be taken back: a subject line shows on a locked phone, on a smartwatch, and in the
     * preview pane of a screen someone else can see. Anyone within sight of the device could then
     * reset the account without ever unlocking it — which defeats the point of mailing a code at all,
     * since the whole mechanism assumes only the mailbox owner can read it.
     *
     * <p>The preview line is chosen for the same reason: clients show the first text they find, and
     * without one set here that would have been the code itself, straight back onto the lock screen.
     */
    static Message passwordReset(String code, long minutes) {
        String text = """
                Someone asked to reset the password for this account.

                Use this code to set a new password:

                    %s

                It expires in %d minutes and can be used once.

                If you didn't ask to reset your password, you can ignore this — nothing has changed, \
                and whoever asked cannot get in without this code.""".formatted(code, minutes);

        String html = EmailLayout.page("A code to set a new password on your Orbit account.",
                EmailLayout.heading("Reset your password")
                        + EmailLayout.paragraph("Someone asked to reset the password for this Orbit account. "
                                + "Enter this code to set a new one:")
                        + EmailLayout.code(code)
                        + EmailLayout.paragraph("It expires in <strong>" + minutes
                                + " minutes</strong> and can be used once.")
                        + EmailLayout.muted("If you didn't ask for this, you can ignore it — nothing has "
                                + "changed, and whoever asked cannot get in without the code."));

        return new Message("Password reset assistance for your Orbit account", text, html);
    }

    static Message trialAcknowledgement(String contactName) {
        String text = """
                Hi %s,

                Thanks for asking about a free trial of Orbit. Your request has reached us and someone \
                will come back to you shortly to set your workspace up.

                There's nothing else you need to do — we'll send your sign-in details once your trial \
                is ready.

                — The Orbit team""".formatted(contactName);

        String html = EmailLayout.page("We've got your trial request — nothing else to do for now.",
                EmailLayout.heading("Thanks — we've got your request")
                        + EmailLayout.paragraph("Hi " + escape(contactName) + ",")
                        + EmailLayout.paragraph("Thanks for asking about a free trial of Orbit. Someone "
                                + "will come back to you shortly to set your workspace up.")
                        + EmailLayout.paragraph("There's nothing else you need to do — we'll send your "
                                + "sign-in details once your trial is ready.")
                        + EmailLayout.muted("— The Orbit team"));

        return new Message("We've got your Orbit trial request", text, html);
    }

    static Message trialApproved(String companyName, String loginUrl) {
        String text = """
                Good news — the Orbit workspace for %s is set up and waiting.

                Sign in here:
                %s

                Your sign-in details are coming separately from the person you've been speaking to.

                — The Orbit team""".formatted(companyName, loginUrl);

        String html = EmailLayout.page("Your Orbit workspace is set up and waiting.",
                EmailLayout.heading("Your Orbit trial is ready")
                        + EmailLayout.paragraph("The workspace for <strong>" + escape(companyName)
                                + "</strong> is set up and waiting.")
                        + EmailLayout.button("Sign in to Orbit", loginUrl)
                        + EmailLayout.fallbackLink(loginUrl)
                        + EmailLayout.muted("Your sign-in details are coming separately from the person "
                                + "you've been speaking to."));

        return new Message("Your Orbit trial is ready", text, html);
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(' ').append(value).append('\n');
        }
    }

    private static void fieldIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(EmailLayout.field(label, value));
        }
    }
}
