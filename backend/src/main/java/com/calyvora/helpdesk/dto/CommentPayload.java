package com.calyvora.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Add a message to a ticket thread. */
public record CommentPayload(@NotBlank @Size(max = 4000) String body) {
}
