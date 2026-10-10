package com.calyvora.email;

import com.calyvora.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one {@link EmailService}: resolves which mailbox to send from, hands the message to the
 * matching transport, and decides what a failure means.
 *
 * <p>Failures are reported, not thrown. A mail outage must never roll back a completed registration
 * or invite — but the caller gets an {@link EmailResult} so the UI can tell the truth about whether
 * anything was actually sent, instead of showing "check your email" for a message that never left.
 *
 * <p>When the dev mailbox exists (the {@code embedded} profile) every link is captured there first,
 * whichever transport is active and whether or not the send succeeds — the link is most needed
 * precisely when delivery is broken.
 */
@Service
public class DispatchingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DispatchingEmailService.class);

    private final EmailSettingsResolver resolver;
    private final Map<EmailSettings.Provider, EmailSender> senders = new EnumMap<>(EmailSettings.Provider.class);
    private final ObjectProvider<DevMailbox> mailbox;

    public DispatchingEmailService(EmailSettingsResolver resolver, List<EmailSender> senders,
                                   ObjectProvider<DevMailbox> mailbox) {
        this.resolver = resolver;
        this.mailbox = mailbox;
        for (EmailSender sender : senders) {
            this.senders.put(sender.provider(), sender);
        }
    }

    @Override
    public EmailResult sendVerificationEmail(String to, String verificationUrl) {
        EmailMessages.Message message = EmailMessages.verification(verificationUrl);
        record(to, message.subject(), verificationUrl);
        return send(to, message.subject(), message.body(), message.html());
    }

    @Override
    public EmailResult sendInvitationEmail(String to, String companyName, String acceptUrl) {
        EmailMessages.Message message = EmailMessages.invitation(companyName, acceptUrl);
        record(to, message.subject(), acceptUrl);
        return send(to, message.subject(), message.body(), message.html());
    }

    @Override
    public EmailResult sendPasswordResetCode(String to, String code, long expiresInMinutes) {
        EmailMessages.Message message = EmailMessages.passwordReset(code, expiresInMinutes);
        // The dev mailbox holds the code itself rather than a link, which is the whole payload here —
        // it is what makes the flow testable on a deployment with no mail provider configured.
        record(to, message.subject(), code);
        // The only message sent from the one-time-code address. A code is the mail an attacker most
        // wants to imitate, so it is worth a sender a reader can learn: if codes always arrive from
        // noreply@, one arriving from anywhere else is visibly wrong.
        return send(to, message.subject(), message.body(), message.html(), EmailSettings::forOneTimeCode);
    }

    @Override
    public EmailResult sendTrialRequestNotification(String to, TrialEnquiry enquiry) {
        EmailMessages.Message message = EmailMessages.trialEnquiry(enquiry);
        record(to, message.subject(), enquiry.consoleUrl());
        return send(to, message.subject(), message.body(), message.html());
    }

    @Override
    public EmailResult sendTrialRequestAcknowledgement(String to, String contactName) {
        EmailMessages.Message message = EmailMessages.trialAcknowledgement(contactName);
        record(to, message.subject(), null);
        return send(to, message.subject(), message.body(), message.html());
    }

    @Override
    public EmailResult sendWelcomeEmail(String to, String firstName, String companyName,
                                        String temporaryPassword, String loginUrl) {
        EmailMessages.Message message = EmailMessages.welcome(firstName, companyName, to, temporaryPassword, loginUrl);
        // The dev mailbox keeps the link, never the password: it is readable on a demo deployment.
        record(to, message.subject(), loginUrl);
        return send(to, message.subject(), message.body(), message.html());
    }

    /** The settings a send from the current tenant would use — for the diagnostic endpoint. */
    public EmailSettings currentSettings() {
        return resolver.resolve(TenantContext.getCompanyIdOrNull());
    }

    /**
     * Sends without swallowing the failure, for {@code /api/v1/dev/test-email}. The normal path hides
     * errors on purpose, which makes a misconfigured mailbox indistinguishable from a working one —
     * this is the way to get the provider's actual complaint back.
     */
    public void sendOrThrow(String to, String subject, String body) throws Exception {
        EmailSettings settings = currentSettings();
        senderFor(settings).send(settings, to, subject, body, null);
    }

    private EmailResult send(String to, String subject, String body, String html) {
        return send(to, subject, body, html, java.util.function.UnaryOperator.identity());
    }

    @Override
    public EmailResult sendCheckoutReminder(String to, String firstName, String companyName, String day,
                                            String checkIn, String link) {
        String subject = "You didn't check out on " + day;
        String body = "Hi " + (firstName == null ? "there" : firstName) + ",\n\n"
                + "You checked in at " + checkIn + " on " + day + " but never checked out, so that day's hours are incomplete.\n\n"
                + "Add your check-out time here — your manager will approve it:\n" + link + "\n\n"
                + "If you have already sorted it out, ignore this.\n\n" + (companyName == null ? "Orbit" : companyName);
        String html = "<div style=\"font-family:Arial,sans-serif;font-size:14px;line-height:1.6;color:#222\">"
                + "<p>Hi " + org.springframework.web.util.HtmlUtils.htmlEscape(firstName == null ? "there" : firstName) + ",</p>"
                + "<p>You checked in at <b>" + org.springframework.web.util.HtmlUtils.htmlEscape(checkIn) + "</b> on <b>"
                + org.springframework.web.util.HtmlUtils.htmlEscape(day) + "</b> but never checked out, so that day's hours are incomplete.</p>"
                + "<p><a href=\"" + org.springframework.web.util.HtmlUtils.htmlEscape(link) + "\" style=\"display:inline-block;background:#7c5cff;color:#fff;padding:10px 16px;border-radius:8px;text-decoration:none\">Add my check-out time</a></p>"
                + "<p style=\"color:#666\">Your manager approves the correction. If you have already sorted it out, ignore this.</p></div>";
        return send(to, subject, body, html);
    }

    @Override
    public EmailResult sendDocument(String to, String companyName, String replyTo, String subject,
                                    String message, EmailSender.Attachment attachment) {
        EmailSettings base = resolver.resolve(TenantContext.getCompanyIdOrNull());
        // Replies go to the company, not to the platform's no-reply — a new joinee answering their
        // offer letter must reach the people who sent it.
        EmailSettings settings = replyTo == null || replyTo.isBlank() ? base
                : new EmailSettings(base.provider(), base.from(), replyTo, base.otpFrom(), base.apiKey(), base.apiUrl(),
                        base.host(), base.port(), base.username(), base.password(), base.auth(), base.starttls(), base.ssl());
        String provider = settings.provider().name();
        String html = "<div style=\"font-family:Arial,sans-serif;font-size:14px;line-height:1.6;color:#222\">"
                + org.springframework.web.util.HtmlUtils.htmlEscape(message).replace("\n", "<br>") + "</div>";
        try {
            EmailSender sender = senderFor(settings);
            sender.send(settings, to, subject, message, html, companyName, java.util.List.of(attachment));
            if (sender instanceof ConsoleSender) {
                return EmailResult.failed(provider,
                        "No mail provider is configured, so nothing was delivered — the message was only written to the server log.");
            }
            return EmailResult.ok(provider);
        } catch (Exception ex) {
            String error = describe(ex);
            log.warn("Failed to send document '{}' to {} via {}: {}", subject, to, provider, error);
            return EmailResult.failed(provider, error);
        }
    }

    /**
     * @param senderIdentity adjusts the resolved settings for this one message — used only to swap the
     *                       From for a one-time code. A function rather than a field so the choice is
     *                       made at the call that knows what kind of message this is, and cannot leak
     *                       to the others.
     */
    private EmailResult send(String to, String subject, String body, String html,
                             java.util.function.UnaryOperator<EmailSettings> senderIdentity) {
        EmailSettings settings = senderIdentity.apply(resolver.resolve(TenantContext.getCompanyIdOrNull()));
        String provider = settings.provider().name();
        try {
            EmailSender sender = senderFor(settings);
            sender.send(settings, to, subject, body, html);
            if (sender instanceof ConsoleSender) {
                // The console transport cannot fail, because it only writes to the log — so reporting
                // it as delivered would tell someone to check an inbox nothing is coming to. The link
                // is still in the dev mailbox and the log; the caller just mustn't promise delivery.
                // Keyed on the transport rather than the provider name so a stub registered under any
                // provider still reports what it actually did.
                log.debug("Wrote email '{}' for {} to the log; no mail provider is configured", subject, to);
                return EmailResult.failed(provider,
                        "No mail provider is configured, so nothing was delivered — the message was "
                                + "only written to the server log.");
            }
            log.debug("Sent email '{}' to {} via {}", subject, to, provider);
            return EmailResult.ok(provider);
        } catch (Exception ex) {
            // Catch broadly: transports fail with everything from IOException to unchecked
            // MailConnectException, and none of them may break the flow that triggered the send.
            String error = describe(ex);
            log.warn("Failed to send email '{}' to {} via {}: {}", subject, to, provider, error);
            return EmailResult.failed(provider, error);
        }
    }

    private EmailSender senderFor(EmailSettings settings) {
        EmailSender sender = senders.get(settings.provider());
        if (sender == null) {
            throw new IllegalStateException("No email sender is registered for " + settings.provider());
        }
        return sender;
    }

    /**
     * Capture a link in the dev mailbox — but only when nothing real is being delivered.
     *
     * <p>The mailbox exists so a deployment with no mail provider can still surface verification and
     * invite links, and password-reset <em>codes</em>. That is a credential store by another name:
     * the {@code /api/v1/dev/mailbox} endpoint is public, so whatever lands here can be read by
     * anyone who can reach the server. That is acceptable only when it is the <b>sole</b> copy —
     * i.e. when the transport is {@link EmailSettings.Provider#CONSOLE} and no mail actually left.
     *
     * <p>The moment a real provider delivers, the recipient already has the message, and a second
     * readable copy on a public URL is pure downside: request a reset for any account, read the code
     * here, take the account. So when the resolved provider delivers, nothing is recorded. This is a
     * runtime guard rather than a profile one because the provider is resolved per tenant and can
     * only be known at send time — a deployment mislabeled as non-prod must still not leak codes.
     */
    private void record(String to, String subject, String link) {
        DevMailbox box = mailbox.getIfAvailable();
        if (box == null) {
            return;
        }
        EmailSettings settings = resolver.resolve(TenantContext.getCompanyIdOrNull());
        if (settings.provider() != EmailSettings.Provider.CONSOLE) {
            // A real provider will deliver this; the recipient does not need a public copy, and an
            // attacker must not have one.
            return;
        }
        box.record(to, subject, link);
    }

    /** Root-cause message — JavaMail buries the useful text (auth refused, connect timeout) in the cause. */
    public static String describe(Throwable ex) {
        StringBuilder sb = new StringBuilder(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            sb.append(" | caused by ").append(cause.getClass().getSimpleName())
                    .append(": ").append(cause.getMessage());
        }
        return sb.toString();
    }

    /** Unused today; kept explicit so the per-tenant resolver has an obvious call site. */
    EmailSettings settingsFor(UUID companyId) {
        return resolver.resolve(companyId);
    }
}
