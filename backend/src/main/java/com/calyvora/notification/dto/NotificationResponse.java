package com.calyvora.notification.dto;

import com.calyvora.notification.Notification;

/** An inbox entry. */
public record NotificationResponse(
        String id,
        String type,
        String title,
        String body,
        String link,
        String entityType,
        String entityId,
        boolean read,
        String createdAt
) {
    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(n.getId().toString(), n.getType(), n.getTitle(), n.getBody(),
                n.getLink(), n.getEntityType(),
                n.getEntityId() == null ? null : n.getEntityId().toString(),
                n.getReadAt() != null, n.getCreatedAt().toString());
    }
}
