package com.calyvora.email;

import com.calyvora.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dev mailbox behind {@code /dev/mailbox} is how a developer gets at a verification or invite
 * link locally. It must be fed by whichever sender is active — it once hung off the console sender
 * alone, so configuring real SMTP silently emptied the mailbox and left no link to click.
 */
class SmtpEmailServiceTest {

    private static final AppProperties PROPS = new AppProperties(
            null, null, new AppProperties.Mail("no-reply@calyvora.local"), null);

    @Test
    void records_the_verification_link_in_the_dev_mailbox() {
        DevMailbox mailbox = new DevMailbox();
        SmtpEmailService service = new SmtpEmailService(mock(JavaMailSender.class), PROPS, provider(mailbox));

        service.sendVerificationEmail("someone@example.com", "http://localhost:3000/verify-email?token=abc");

        assertThat(mailbox.list()).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo("someone@example.com");
            assertThat(m.link()).endsWith("token=abc");
        });
    }

    @Test
    void records_the_link_even_when_the_send_fails() {
        // Precisely when delivery is broken you still need the link to finish the flow by hand.
        DevMailbox mailbox = new DevMailbox();
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down")).when(sender).send(any(SimpleMailMessage.class));
        SmtpEmailService service = new SmtpEmailService(sender, PROPS, provider(mailbox));

        service.sendInvitationEmail("invitee@example.com", "Northwind", "http://localhost:3000/accept-invite?token=xyz");

        assertThat(mailbox.list()).singleElement()
                .satisfies(m -> assertThat(m.link()).endsWith("token=xyz"));
    }

    @Test
    void works_without_a_mailbox_bean() {
        // Outside the embedded profile no DevMailbox exists; sending must not NPE.
        SmtpEmailService service = new SmtpEmailService(mock(JavaMailSender.class), PROPS, provider(null));

        service.sendVerificationEmail("someone@example.com", "http://x/verify-email?token=abc");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DevMailbox> provider(DevMailbox mailbox) {
        ObjectProvider<DevMailbox> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailbox);
        return provider;
    }
}
