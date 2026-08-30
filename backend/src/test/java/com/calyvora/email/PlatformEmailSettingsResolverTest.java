package com.calyvora.email;

import com.calyvora.common.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Choosing the transport. The case that mattered in production: {@code MAIL_PROVIDER=resend} was set
 * in the deploy blueprint while {@code RESEND_API_KEY} was left unset, so auto-detection was skipped
 * and every send threw — the deployment mailed nothing at all, silently, for as long as it ran.
 */
class PlatformEmailSettingsResolverTest {

    @Test
    void a_named_provider_without_its_credential_falls_back_to_the_console() {
        EmailSettings settings = resolve("resend", "", "");

        assertThat(settings.provider()).isEqualTo(EmailSettings.Provider.CONSOLE);
    }

    @Test
    void smtp_named_without_a_username_falls_back_too() {
        assertThat(resolve("smtp", "", "").provider()).isEqualTo(EmailSettings.Provider.CONSOLE);
    }

    @Test
    void a_named_provider_with_its_credential_is_honoured() {
        assertThat(resolve("resend", "re_live_key", "").provider()).isEqualTo(EmailSettings.Provider.RESEND);
        assertThat(resolve("smtp", "", "postmaster@x.io").provider()).isEqualTo(EmailSettings.Provider.SMTP);
    }

    @Test
    void with_nothing_named_the_available_credential_decides() {
        assertThat(resolve("", "re_live_key", "").provider()).isEqualTo(EmailSettings.Provider.RESEND);
        assertThat(resolve("", "", "postmaster@x.io").provider()).isEqualTo(EmailSettings.Provider.SMTP);
        assertThat(resolve("", "", "").provider()).isEqualTo(EmailSettings.Provider.CONSOLE);
    }

    private static EmailSettings resolve(String configuredProvider, String apiKey, String smtpUsername) {
        // Only the mail block matters here; the rest of AppProperties is inert for this resolver.
        AppProperties props = new AppProperties(
                "http://localhost:3000", java.util.List.of(),
                new AppProperties.Mail(configuredProvider, "no-reply@calyvora.test", "hello@calyvora.test",
                        new AppProperties.Resend(apiKey, "https://api.resend.com/emails")),
                null, null);
        return new PlatformEmailSettingsResolver(props, configuredProvider, "localhost", 1025,
                smtpUsername, "", false, false, false).resolve(null);
    }
}
