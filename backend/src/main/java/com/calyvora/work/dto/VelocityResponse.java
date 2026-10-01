package com.calyvora.work.dto;

import java.util.List;

/**
 * Completed points per finished sprint, plus the average — the number a team plans the next sprint
 * with. Only COMPLETED sprints count: an in-flight sprint would drag the average down for no reason.
 */
public record VelocityResponse(
        List<SprintVelocity> sprints,
        double averageVelocity,
        /** Suggested commitment for the next sprint: the rolling average, rounded. */
        int suggestedCommitment
) {
    public record SprintVelocity(
            String sprintId,
            String name,
            String endDate,
            int committedPoints,
            int completedPoints
    ) {}
}
