package com.calyvora.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
        @NotBlank @Size(min = 1, max = 120) String name,
        String parentId,
        String leadUserId
) {
}
