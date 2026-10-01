package com.calyvora.work.dto;

import com.calyvora.work.Task;

public record TaskResponse(
        String id,
        String projectId,
        String ref,
        int number,
        String title,
        String description,
        String status,
        String priority,
        String assigneeId,
        String assigneeName,
        String sprintId,
        String dueDate,
        /** Estimate; null when the task hasn't been sized yet. */
        Integer storyPoints,
        String createdAt
) {
    public static TaskResponse of(Task t, String projectKey, String assigneeName) {
        return new TaskResponse(
                t.getId().toString(), t.getProjectId().toString(),
                projectKey + "-" + t.getNumber(), t.getNumber(), t.getTitle(), t.getDescription(),
                t.getStatus().name(), t.getPriority().name(),
                t.getAssigneeId() == null ? null : t.getAssigneeId().toString(), assigneeName,
                t.getSprintId() == null ? null : t.getSprintId().toString(),
                t.getDueDate() == null ? null : t.getDueDate().toString(),
                t.getStoryPoints(),
                t.getCreatedAt().toString());
    }
}
