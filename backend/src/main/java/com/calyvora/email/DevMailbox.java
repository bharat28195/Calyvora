package com.calyvora.email;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory capture of "emails" so verification and invite links are visible in the app at
 * {@code /dev/mailbox} without a real mail provider. Newest first, capped so it can't grow unbounded.
 *
 * <p>Active in every profile except {@code prod}, staging included — a demo or staging deployment is
 * exactly where mail is least likely to be configured and the link is most needed. <b>It is not a
 * secret store:</b> anyone who can reach the deployment can read these links and therefore act on a
 * pending verification or invitation. That is acceptable for a demo tenant and is the reason the
 * bean does not exist under {@code prod}.
 */
@Component
@Profile("!prod")
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
