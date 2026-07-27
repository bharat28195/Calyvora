package com.calyvora.helpdesk.dto;

import com.calyvora.helpdesk.HelpdeskTicket;

import java.util.Map;
import java.util.UUID;

/** A helpdesk ticket with resolved people names + comment count. */
public record TicketResponse(
        String id,
        String category,
        String subject,
        String description,
        String priority,
        String status,
        String raisedById,
        String raisedByName,
        String assigneeId,
        String assigneeName,
        long commentCount,
        String createdAt,
        String updatedAt,
        String resolvedAt
) {
    public static TicketResponse of(HelpdeskTicket t, Map<UUID, String> names, long commentCount) {
        return new TicketResponse(
                t.getId().toString(), t.getCategory().name(), t.getSubject(), t.getDescription(),
                t.getPriority().name(), t.getStatus().name(),
                t.getRaisedBy().toString(), names.getOrDefault(t.getRaisedBy(), "Someone"),
                t.getAssigneeId() == null ? null : t.getAssigneeId().toString(),
                t.getAssigneeId() == null ? null : names.get(t.getAssigneeId()),
                commentCount,
                t.getCreatedAt().toString(), t.getUpdatedAt().toString(),
                t.getResolvedAt() == null ? null : t.getResolvedAt().toString());
    }
}
