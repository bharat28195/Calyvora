package com.calyvora.people.dto;

import jakarta.validation.constraints.Size;

/** All fields optional; nulls leave values unchanged, empty string clears parent/lead. */
public record UpdateDepartmentRequest(
        @Size(min = 1, max = 120) String name,
        String parentId,
        String leadUserId
) {
}
