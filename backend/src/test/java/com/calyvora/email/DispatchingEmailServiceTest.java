package com.calyvora.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two behaviours this class exists to guarantee:
 *
 * <p>The dev mailbox behind {@code /dev/mailbox} is how a developer gets at a verification or invite
 * link locally. It must be fed by whichever transport is active — it once hung off the console
 * sender alone, so configuring real SMTP silently emptied the mailbox and left no link to click.
 *
 * <p>And a send failure must be reported without being thrown: the registration that triggered it
 * has already committed, but the caller has to know so it can offer a resend rather than claim the
 * mail is on its way.
 */
class DispatchingEmailServiceTest {

    private static final EmailSettings CONSOLE = EmailSettings.console("no-reply@calyvora.local");

    @Test
    void records_the_verification_link_in_the_dev_mailbox() {
        DevMailbox mailbox = new DevMailbox();
        DispatchingEmailService service = service(new OkSender(), mailbox);

        EmailResult result = service.sendVerificationEmail(
                "someone@example.com", "http://localhost:3000/verify-email?token=abc");

        assertThat(result.delivered()).isTrue();
        assertThat(mailbox.list()).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo("someone@example.com");
            assertThat(m.link()).endsWith("token=abc");
        });
    }

    @Test
    void records_the_link_even_when_the_send_fails() {
        // Precisely when delivery is broken you still need the link to finish the flow by hand.
        DevMailbox mailbox = new DevMailbox();
        DispatchingEmailService service = service(new FailingSender(), mailbox);

        EmailResult result = service.sendInvitationEmail(
                "invitee@example.com", "Northwind", "http://localhost:3000/accept-invite?token=xyz");

        assertThat(result.delivered()).isFalse();
        assertThat(mailbox.list()).singleElement()
                .satisfies(m -> assertThat(m.link()).endsWith("token=xyz"));
    }

    @Test
    void reports_the_provider_error_instead_of_throwing() {
        // The signup has already committed by this point — throwing here would roll it back.
        DispatchingEmailService service = service(new FailingSender(), null);

        EmailResult result = service.sendVerificationEmail("someone@example.com", "http://x/verify?token=a");

        assertThat(result.delivered()).isFalse();
        assertThat(result.provider()).isEqualTo("CONSOLE");
        assertThat(result.error()).contains("mail host unreachable");
    }

    @Test
    void works_without_a_mailbox_bean() {
        // Outside the embedded profile no DevMailbox exists; sending must not NPE.
        DispatchingEmailService service = service(new OkSender(), null);

        assertThat(service.sendVerificationEmail("someone@example.com", "http://x/verify?token=a").delivered())
                .isTrue();
    }

    @Test
    void the_console_transport_never_claims_to_have_delivered_anything() {
        // It only writes to the log, so it cannot fail — which is exactly why "sent" would be a lie.
        // Signup showed "check your email" on the back of this and left people waiting on nothing.
        DevMailbox mailbox = new DevMailbox();
        DispatchingEmailService service = service(new ConsoleSender(), mailbox);

        EmailResult result = service.sendVerificationEmail("someone@example.com", "http://x/verify?token=a");

        assertThat(result.delivered()).isFalse();
        assertThat(result.error()).contains("No mail provider is configured");
        // The link is still recoverable, which is the whole point of capturing it.
        assertThat(mailbox.list()).singleElement()
                .satisfies(m -> assertThat(m.link()).endsWith("token=a"));
    }

    @Test
    void the_diagnostic_send_propagates_the_failure() {
        // /dev/test-email exists to show the real error, so this one path must not swallow it.
        DispatchingEmailService service = service(new FailingSender(), null);

        assertThatThrownBySending(service);
    }

    private static void assertThatThrownBySending(DispatchingEmailService service) {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.sendOrThrow("a@b.com", "subject", "body"))
                .hasMessageContaining("mail host unreachable");
    }

    private static DispatchingEmailService service(EmailSender sender, DevMailbox mailbox) {
        EmailSettingsResolver resolver = companyId -> CONSOLE;
        return new DispatchingEmailService(resolver, List.of(sender), provider(mailbox));
    }

    /** Both doubles claim CONSOLE so the resolver's settings route to them. */
    private static class OkSender implements EmailSender {
        @Override
        public EmailSettings.Provider provider() {
            return EmailSettings.Provider.CONSOLE;
        }

        @Override
        public void send(EmailSettings settings, String to, String subject, String body) {
            // delivered
        }
    }

    private static class FailingSender implements EmailSender {
        @Override
        public EmailSettings.Provider provider() {
            return EmailSettings.Provider.CONSOLE;
        }

        @Override
        public void send(EmailSettings settings, String to, String subject, String body) {
            throw new IllegalStateException("mail host unreachable");
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DevMailbox> provider(DevMailbox mailbox) {
        ObjectProvider<DevMailbox> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailbox);
        return provider;
    }

    @SuppressWarnings("unused")
    private static UUID anyCompany() {
        return UUID.randomUUID();
    }
}
