package com.calyvora.support;

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

    @Override
    public void sendVerificationEmail(String to, String verificationUrl) {
        verifications.add(new Sent(to, verificationUrl));
    }

    @Override
    public void sendInvitationEmail(String to, String companyName, String acceptUrl) {
        invitations.add(new Sent(to, acceptUrl));
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
    }

    private static String tokenParam(String url) {
        int i = url.indexOf("token=");
        return i < 0 ? null : url.substring(i + "token=".length());
    }
}
