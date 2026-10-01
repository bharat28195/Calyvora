package com.calyvora.work.dto;

import com.calyvora.work.Ticket;

public record TicketResponse(
        String id,
        String projectId,
        String ref,
        int number,
        String subject,
        String description,
        String requesterName,
        String requesterEmail,
        String status,
        String priority,
        String assigneeId,
        String assigneeName,
        String createdAt
) {
    public static TicketResponse of(Ticket t, String projectKey, String assigneeName) {
        return new TicketResponse(
                t.getId().toString(), t.getProjectId().toString(),
                projectKey + "-T" + t.getNumber(), t.getNumber(), t.getSubject(), t.getDescription(),
                t.getRequesterName(), t.getRequesterEmail(),
                t.getStatus().name(), t.getPriority().name(),
                t.getAssigneeId() == null ? null : t.getAssigneeId().toString(), assigneeName,
                t.getCreatedAt().toString());
    }
}
