package com.calyvora.people.dto;

import com.calyvora.people.Goal;

/** An employee goal with its progress and status (feedback C8). */
public record GoalResponse(
        String id,
        String title,
        String description,
        String status,
        int progress,
        String targetDate,
        String createdAt
) {
    public static GoalResponse of(Goal g) {
        return new GoalResponse(
                g.getId().toString(), g.getTitle(), g.getDescription(), g.getStatus().name(),
                g.getProgress(), g.getTargetDate() == null ? null : g.getTargetDate().toString(),
                g.getCreatedAt().toString());
    }
}
