package com.calyvora.work.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** All optional. Empty string clears the assignee. */
public record UpdateTicketRequest(
        @Size(min = 1, max = 200) String subject,
        @Size(max = 4000) String description,
        @Size(max = 160) String requesterName,
        @Size(max = 200) String requesterEmail,
        @Pattern(regexp = "OPEN|PENDING|RESOLVED|CLOSED", message = "invalid status") String status,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT", message = "invalid priority") String priority,
        String assigneeId
) {
}
