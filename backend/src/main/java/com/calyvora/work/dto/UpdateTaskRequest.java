package com.calyvora.work.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** All fields optional. Empty string clears assignee / due date / sprint (moves task to backlog). */
public record UpdateTaskRequest(
        @Size(min = 1, max = 200) String title,
        @Size(max = 4000) String description,
        @Pattern(regexp = "TODO|IN_PROGRESS|DONE", message = "invalid status") String status,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT", message = "invalid priority") String priority,
        String assigneeId,
        String sprintId,
        String dueDate,
        /** Story points; send -1 to clear the estimate. */
        Integer storyPoints
) {
}
