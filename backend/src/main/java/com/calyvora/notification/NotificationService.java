package com.calyvora.notification;

import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.notification.dto.NotificationResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The inbox (feedback D4 + D5). Other modules call {@link #send} when something happens that a
 * person needs to know about — a leave request landing on their desk, a decision on their own
 * request, a goal their manager set them.
 *
 * <p>Two deliberate rules: we never notify someone about their own action (an owner approving their
 * own leave shouldn't get mail about it), and the text is frozen at send time so the entry still
 * reads correctly later.
 */
@Service
public class NotificationService {

    private static final Pageable RECENT = PageRequest.of(0, 50);

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Send to one person. Silently skips self-notification and unknown recipients. */
    @Transactional
    public void send(UUID companyId, UUID recipientId, UUID actorId, NotificationType type,
                     String title, String body, String link, String entityType, UUID entityId) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return;
        }
        repository.save(new Notification(UUID.randomUUID(), companyId, recipientId, actorId, type,
                title, body, link, entityType, entityId));
    }

    /** Send the same thing to several people (e.g. every admin when nobody manages the requester). */
    @Transactional
    public void sendAll(UUID companyId, Collection<UUID> recipientIds, UUID actorId, NotificationType type,
                        String title, String body, String link, String entityType, UUID entityId) {
        for (UUID recipientId : recipientIds) {
            send(companyId, recipientId, actorId, type, title, body, link, entityType, entityId);
        }
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(AuthPrincipal principal, boolean unreadOnly) {
        List<Notification> rows = unreadOnly
                ? repository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(principal.userId(), RECENT)
                : repository.findByRecipientIdOrderByCreatedAtDesc(principal.userId(), RECENT);
        return rows.stream().map(NotificationResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthPrincipal principal) {
        return repository.countByRecipientIdAndReadAtIsNull(principal.userId());
    }

    @Transactional
    public NotificationResponse markRead(UUID id, AuthPrincipal principal) {
        Notification n = repository.findByIdAndRecipientId(id, principal.userId())
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        n.markRead();
        return NotificationResponse.of(n);
    }

    @Transactional
    public int markAllRead(AuthPrincipal principal) {
        return repository.markAllRead(principal.userId(), Instant.now());
    }

    /** Convenience for callers already inside a tenant-scoped request. */
    public UUID companyId() {
        return TenantContext.getCompanyId();
    }
}
