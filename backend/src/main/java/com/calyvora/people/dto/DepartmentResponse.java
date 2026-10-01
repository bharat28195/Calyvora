package com.calyvora.people.dto;

public record DepartmentResponse(
        String id,
        String name,
        String parentId,
        String leadUserId,
        String leadName,
        long memberCount
) {
}
