package com.calyvora.work.dto;

import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(min = 2, max = 120) String name,
        @Size(max = 2000) String description,
        String leadUserId
) {
}
