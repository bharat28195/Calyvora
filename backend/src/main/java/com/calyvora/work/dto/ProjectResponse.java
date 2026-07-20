package com.calyvora.work.dto;

import com.calyvora.work.Project;

public record ProjectResponse(
        String id,
        String name,
        String key,
        String description,
        String status,
        String leadUserId,
        String leadName,
        long taskCount,
        long openTaskCount,
        String createdAt
) {
    public static ProjectResponse of(Project p, String leadName, long taskCount, long openTaskCount) {
        return new ProjectResponse(
                p.getId().toString(), p.getName(), p.getKey(), p.getDescription(),
                p.getStatus().name(),
                p.getLeadUserId() == null ? null : p.getLeadUserId().toString(),
                leadName, taskCount, openTaskCount, p.getCreatedAt().toString());
    }
}
