package com.calyvora.email;

/** A captured dev "email" (local-dev only), surfaced in the in-app dev mailbox. */
public record DevMailMessage(String to, String subject, String link, long sentAt) {
}
