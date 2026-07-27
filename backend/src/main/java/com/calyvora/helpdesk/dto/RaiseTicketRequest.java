package com.calyvora.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An employee raises a helpdesk ticket. */
public record RaiseTicketRequest(
        @NotBlank String category,
        @NotBlank @Size(max = 160) String subject,
        @Size(max = 4000) String description,
        String priority
) {
}
