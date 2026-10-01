package com.calyvora.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSpaceRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Size(min = 1, max = 10) @Pattern(regexp = "[A-Za-z0-9]+", message = "key must be letters/numbers only") String key,
        @Size(max = 2000) String description
) {
}
