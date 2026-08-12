package com.calyvora.support;

import com.calyvora.email.EmailResult;
import com.calyvora.email.EmailService;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double for {@link EmailService}. Instead of sending SMTP, it records the links — letting
 * tests recover the raw verification / invitation token (which only ever lives in the email).
 * Registered via {@code @Import} on the test base and {@code @Primary} so it overrides the real
 * SMTP bean in the test context.
 */
@Primary
public class RecordingEmailService implements EmailService {

    public record Sent(String to, String url) {
    }

    private final List<Sent> verifications = new ArrayList<>();
    private final List<Sent> invitations = new ArrayList<>();
    private final List<Sent> trialNotifications = new ArrayList<>();
    private final List<Sent> trialAcknowledgements = new ArrayList<>();
    private final List<Sent> trialApprovals = new ArrayList<>();

    @Override
    public EmailResult sendVerificationEmail(String to, String verificationUrl) {
        verifications.add(new Sent(to, verificationUrl));
        return EmailResult.ok("RECORDING");
    }

    @Override
    public EmailResult sendInvitationEmail(String to, String companyName, String acceptUrl) {
        invitations.add(new Sent(to, acceptUrl));
        return EmailResult.ok("RECORDING");
    }

    @Override
    public EmailResult sendTrialRequestNotification(String to, com.calyvora.email.TrialEnquiry enquiry) {
        // The whole point of the vendor notification is *who* asked, so the recorded "url" is the
        // enquiry itself — a test that only saw the console link could not tell one request from
        // another, which is exactly what it needs to assert.
        trialNotifications.add(new Sent(to, enquiry.companyName() + " <" + enquiry.email() + ">"));
        return EmailResult.ok("RECORDING");
    }

    @Override
    public EmailResult sendTrialRequestAcknowledgement(String to, String contactName) {
        trialAcknowledgements.add(new Sent(to, contactName));
        return EmailResult.ok("RECORDING");
    }

    @Override
    public EmailResult sendTrialApprovedEmail(String to, String companyName, String loginUrl) {
        trialApprovals.add(new Sent(to, loginUrl));
        return EmailResult.ok("RECORDING");
    }

    public List<Sent> trialNotifications() {
        return trialNotifications;
    }

    public List<Sent> trialAcknowledgements() {
        return trialAcknowledgements;
    }

    public List<Sent> trialApprovals() {
        return trialApprovals;
    }

    public String lastVerificationToken() {
        return tokenParam(verifications.get(verifications.size() - 1).url());
    }

    public String lastInvitationToken() {
        return tokenParam(invitations.get(invitations.size() - 1).url());
    }

    public List<Sent> verifications() {
        return verifications;
    }

    public List<Sent> invitations() {
        return invitations;
    }

    public void clear() {
        verifications.clear();
        invitations.clear();
        trialNotifications.clear();
        trialAcknowledgements.clear();
        trialApprovals.clear();
    }

    private static String tokenParam(String url) {
        int i = url.indexOf("token=");
        return i < 0 ? null : url.substring(i + "token=".length());
    }
}
