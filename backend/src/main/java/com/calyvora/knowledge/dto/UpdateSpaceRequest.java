package com.calyvora.knowledge.dto;

import jakarta.validation.constraints.Size;

public record UpdateSpaceRequest(
        @Size(min = 2, max = 120) String name,
        @Size(max = 2000) String description
) {
}
