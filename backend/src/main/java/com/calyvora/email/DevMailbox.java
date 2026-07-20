package com.calyvora.email;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory capture of dev "emails" so verification/invite links are visible in the app at
 * {@code /dev/mailbox} without a real SMTP server. Local-dev only ({@code embedded} profile);
 * never active in prod. Newest first, capped so it can't grow unbounded.
 */
@Component
@Profile("embedded")
public class DevMailbox {

    private static final int CAP = 50;

    private final Deque<DevMailMessage> messages = new ConcurrentLinkedDeque<>();

    public void record(String to, String subject, String link) {
        messages.addFirst(new DevMailMessage(to, subject, link, System.currentTimeMillis()));
        while (messages.size() > CAP) {
            messages.pollLast();
        }
    }

    public List<DevMailMessage> list() {
        return new ArrayList<>(messages);
    }

    public void clear() {
        messages.clear();
    }
}
