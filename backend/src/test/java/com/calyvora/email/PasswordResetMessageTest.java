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
        // this is the one mail someone is actively waiting for. It names the PRODUCT, not the
        // company — the reader recognises what they signed in to, and has likely never seen
        // "Calyvora" anywhere.
        EmailMessages.Message message = EmailMessages.passwordReset(CODE, 15);

        assertThat(message.subject().toLowerCase())
                .contains("password")
                .contains("orbit");
    }

    @Test
    void both_bodies_carry_the_code_and_its_expiry() {
        EmailMessages.Message message = EmailMessages.passwordReset(CODE, 15);

        assertThat(message.body())
                .as("moving the code out of the subject must not lose it altogether")
                .contains(CODE)
                .contains("15 minutes");
        // The HTML part is what almost everyone actually sees; a code present in only one of the two
        // alternatives means the message is broken for whichever half the client chooses to render.
        assertThat(message.html())
                .contains(CODE)
                .contains("15 minutes");
    }

    @Test
    void the_html_part_is_a_whole_document_and_escapes_what_it_interpolates() {
        EmailMessages.Message message = EmailMessages.passwordReset(CODE, 15);

        assertThat(message.html()).startsWith("<!doctype html>").contains("</html>");
        // Names and notes reach these templates from a public form. Left unescaped, a stranger could
        // put markup into a message we send under our own domain.
        assertThat(EmailLayout.escape("<script>x</script>&\"")).isEqualTo("&lt;script&gt;x&lt;/script&gt;&amp;&quot;");
    }
}
