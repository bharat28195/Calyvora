package com.calyvora.helpdesk.dto;

/** HR/admin updates a ticket — status, assignee, priority or category. All optional (patch). */
public record UpdateTicketRequest(
        String status,
        String assigneeId,
        String priority,
        String category
) {
}
