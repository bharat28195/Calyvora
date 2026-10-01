package com.calyvora.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends over Resend's HTTPS API instead of SMTP.
 *
 * <p>This exists because SMTP is not reachable from many hosts: Render's free tier (and plenty of
 * cloud providers) drop outbound 25/587/465 outright, which looks exactly like a hung connection.
 * Port 443 is never blocked, so mail keeps working wherever the app is deployed.
 */
@Component
public class ResendSender implements EmailSender {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient http;
    private final ObjectMapper json;

    public ResendSender(ObjectMapper json) {
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public EmailSettings.Provider provider() {
        return EmailSettings.Provider.RESEND;
    }

    @Override
    public void send(EmailSettings settings, String to, String subject, String body, String html) throws Exception {
        if (settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new IllegalStateException("No Resend API key configured (set RESEND_API_KEY)");
        }
        // Both parts, always. The client picks; filters read the text one, and a message with no text
        // alternative is scored as more likely to be spam for exactly that reason.
        Map<String, Object> payloadMap = new LinkedHashMap<>(Map.of(
                "from", EmailIdentity.from(settings),
                "to", List.of(to),
                "subject", subject,
                "text", body));
        if (html != null && !html.isBlank()) {
            payloadMap.put("html", html);
        }
        if (settings.replyTo() != null && !settings.replyTo().isBlank()) {
            payloadMap.put("reply_to", settings.replyTo());
        }
        byte[] payload = json.writeValueAsBytes(payloadMap);

        HttpRequest request = HttpRequest.newBuilder(URI.create(settings.apiUrl()))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + settings.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            // Surface Resend's own wording — "domain not verified" and "invalid api key" are the two
            // failures worth recognising instantly, and only the provider can name them.
            throw new IllegalStateException("Resend returned " + response.statusCode() + ": "
                    + describe(response.body()));
        }
    }

    /** Resend reports errors as {@code {"message": "..."}}; fall back to the raw body if it isn't. */
    private String describe(String body) {
        if (body == null || body.isBlank()) {
            return "(empty response)";
        }
        try {
            var node = json.readTree(body);
            var message = node.get("message");
            return message != null && message.isTextual() ? message.asText() : body;
        } catch (Exception ex) {
            return body;
        }
    }
}
