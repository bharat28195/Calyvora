package com.calyvora.people.dto;

import com.calyvora.people.OnboardingTask;

public record OnboardingTaskResponse(
        String id,
        String employeeId,
        String title,
        int sortOrder,
        boolean completed,
        String completedAt
) {
    public static OnboardingTaskResponse of(OnboardingTask t) {
        return new OnboardingTaskResponse(
                t.getId().toString(), t.getEmployeeId().toString(), t.getTitle(),
                t.getSortOrder(), t.isCompleted(),
                t.getCompletedAt() == null ? null : t.getCompletedAt().toString());
    }
}
