package com.calyvora.work.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * All fields optional — only what is sent is changed.
 *
 * <p>The commercial fields (V44, D2) are what give a project a margin. Send {@code clientId} as an
 * empty string to detach the project from its client, and a negative {@code contractValue} to clear
 * the agreed fee — the same "empty clears it" idiom {@link UpdateTaskRequest} uses for assignee.
 */
public record UpdateProjectRequest(
        @Size(min = 2, max = 120) String name,
        @Size(max = 2000) String description,
        String leadUserId,
        String clientId,
        Boolean billable,
        BigDecimal contractValue
) {
}
