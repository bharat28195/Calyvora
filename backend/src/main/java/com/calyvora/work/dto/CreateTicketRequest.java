package com.calyvora.work.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(min = 1, max = 200) String subject,
        @Size(max = 4000) String description,
        @Size(max = 160) String requesterName,
        @Email @Size(max = 200) String requesterEmail,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT", message = "invalid priority") String priority,
        String assigneeId
) {
}
