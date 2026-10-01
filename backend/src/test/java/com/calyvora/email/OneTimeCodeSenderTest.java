package com.calyvora.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A one-time code is sent from its own address; nothing else is.
 *
 * <p>The reason it is worth a separate sender at all: a reset code is the message an attacker most
 * wants to imitate, and a reader who learns that codes always arrive from the same address has
 * something to check a forgery against. That only holds if it is used consistently for codes and
 * never for anything else — which is what these tests hold in place.
 */
class OneTimeCodeSenderTest {

    private static final EmailSettings SETTINGS = EmailSettings.console(
            "connect@calyvora.in", "connect@calyvora.in", "noreply@calyvora.in");

    @Test
    void a_reset_code_is_sent_from_the_one_time_code_address() {
        RecordingSender sender = new RecordingSender();

        service(sender).sendPasswordResetCode("someone@example.com", "483920", 15);

        assertThat(sender.lastFrom).isEqualTo("noreply@calyvora.in");
    }

    @Test
    void everything_else_is_sent_from_the_ordinary_address() {
        RecordingSender sender = new RecordingSender();
        DispatchingEmailService service = service(sender);

        service.sendInvitationEmail("invitee@example.com", "Northwind", "https://orbit.calyvora.in/x");
        assertThat(sender.lastFrom).as("an invitation is a conversation, not a credential")
                .isEqualTo("connect@calyvora.in");

        service.sendTrialRequestAcknowledgement("someone@example.com", "Sam");
        assertThat(sender.lastFrom).isEqualTo("connect@calyvora.in");
    }

    @Test
    void a_reply_still_reaches_a_person_even_on_the_code_email() {
        // The whole risk of a no-reply sender is that a confused recipient replies into a void.
        RecordingSender sender = new RecordingSender();

        service(sender).sendPasswordResetCode("someone@example.com", "483920", 15);

        assertThat(sender.lastReplyTo).isEqualTo("connect@calyvora.in");
    }

    @Test
    void an_unset_code_address_falls_back_to_the_ordinary_one() {
        // A deployment that never configured it must still send, not send as null.
        RecordingSender sender = new RecordingSender();
        EmailSettings noOtp = EmailSettings.console("connect@calyvora.in", null, null);

        new DispatchingEmailService(companyId -> noOtp, List.of(sender), emptyProvider())
                .sendPasswordResetCode("someone@example.com", "483920", 15);

        assertThat(sender.lastFrom).isEqualTo("connect@calyvora.in");
    }

    private static DispatchingEmailService service(EmailSender sender) {
        return new DispatchingEmailService(companyId -> SETTINGS, List.of(sender), emptyProvider());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DevMailbox> emptyProvider() {
        ObjectProvider<DevMailbox> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    /** Claims CONSOLE so the resolved settings route to it, and remembers the identity it was given. */
    private static class RecordingSender implements EmailSender {
        private String lastFrom;
        private String lastReplyTo;

        @Override
        public EmailSettings.Provider provider() {
            return EmailSettings.Provider.CONSOLE;
        }

        @Override
        public void send(EmailSettings settings, String to, String subject, String body, String html) {
            this.lastFrom = settings.from();
            this.lastReplyTo = settings.replyTo();
        }
    }
}
