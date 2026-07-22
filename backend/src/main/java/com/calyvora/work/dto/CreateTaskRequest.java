package com.calyvora.work.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank @Size(min = 1, max = 200) String title,
        @Size(max = 4000) String description,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT", message = "invalid priority") String priority,
        String assigneeId,
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "due date must be YYYY-MM-DD") String dueDate,
        Integer storyPoints
) {
}
