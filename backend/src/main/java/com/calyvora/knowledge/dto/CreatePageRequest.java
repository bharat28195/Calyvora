package com.calyvora.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePageRequest(
        @NotBlank @Size(min = 1, max = 200) String title,
        String body,
        String parentId,
        String linkedTaskId
) {
}
