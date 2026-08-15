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
        return send(to, message.subject(), message.body());
    }

    @Override
    public EmailResult sendInvitationEmail(String to, String companyName, String acceptUrl) {
        EmailMessages.Message message = EmailMessages.invitation(companyName, acceptUrl);
        record(to, message.subject(), acceptUrl);
        return send(to, message.subject(), message.body());
    }

    @Override
    public EmailResult sendPasswordResetCode(String to, String code, long expiresInMinutes) {
        EmailMessages.Message message = EmailMessages.passwordReset(code, expiresInMinutes);
        // The dev mailbox holds the code itself rather than a link, which is the whole payload here —
        // it is what makes the flow testable on a deployment with no mail provider configured.
        record(to, message.subject(), code);
        return send(to, message.subject(), message.body());
    }

    @Override
    public EmailResult sendTrialRequestNotification(String to, TrialEnquiry enquiry) {
        EmailMessages.Message message = EmailMessages.trialEnquiry(enquiry);
        record(to, message.subject(), enquiry.consoleUrl());
        return send(to, message.subject(), message.body());
    }

    @Override
    public EmailResult sendTrialRequestAcknowledgement(String to, String contactName) {
        EmailMessages.Message message = EmailMessages.trialAcknowledgement(contactName);
        record(to, message.subject(), null);
        return send(to, message.subject(), message.body());
    }

    @Override
    public EmailResult sendTrialApprovedEmail(String to, String companyName, String loginUrl) {
        EmailMessages.Message message = EmailMessages.trialApproved(companyName, loginUrl);
        record(to, message.subject(), loginUrl);
        return send(to, message.subject(), message.body());
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
        senderFor(settings).send(settings, to, subject, body);
    }

    private EmailResult send(String to, String subject, String body) {
        EmailSettings settings = resolver.resolve(TenantContext.getCompanyIdOrNull());
        String provider = settings.provider().name();
        try {
            EmailSender sender = senderFor(settings);
            sender.send(settings, to, subject, body);
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

    /** No-op outside the {@code embedded} profile, where no {@link DevMailbox} bean exists. */
    private void record(String to, String subject, String link) {
        DevMailbox box = mailbox.getIfAvailable();
        if (box != null) {
            box.record(to, subject, link);
        }
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
