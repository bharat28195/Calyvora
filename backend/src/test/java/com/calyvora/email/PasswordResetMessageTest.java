package com.calyvora.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reset code must not appear in the subject line.
 *
 * <p>This is pinned rather than left to a comment because the change that breaks it is an appealing
 * one: putting the code in the subject saves the reader a tap, and someone will propose it again.
 * The cost is that a subject is displayed where the body is not — a locked phone, a smartwatch, a
 * preview pane visible across a desk — so the credential is legible to anyone near the device without
 * unlocking it. The entire mechanism assumes only the person who can open the mailbox learns the
 * code, and a subject line quietly breaks that assumption.
 */
class PasswordResetMessageTest {

    private static final String CODE = "483920";

    @Test
    void the_code_is_not_in_the_subject() {
        EmailMessages.Message message = EmailMessages.passwordReset(CODE, 15);

        assertThat(message.subject())
                .as("a subject is readable on a locked screen; the code must not be")
                .doesNotContain(CODE);
    }

    @Test
    void the_subject_still_says_what_the_mail_is_for() {
        // An unrecognisable subject is its own failure: people delete what looks like phishing, and
        // this is the one mail someone is actively waiting for.
        EmailMessages.Message message = EmailMessages.passwordReset(CODE, 15);

        assertThat(message.subject().toLowerCase())
                .contains("password")
                .contains("calyvora");
    }

    @Test
    void the_body_carries_the_code_and_its_expiry() {
        EmailMessages.Message message = EmailMessages.passwordReset(CODE, 15);

        assertThat(message.body())
                .as("moving the code out of the subject must not lose it altogether")
                .contains(CODE)
                .contains("15 minutes");
    }
}
