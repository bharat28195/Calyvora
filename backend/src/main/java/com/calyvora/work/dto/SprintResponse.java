package com.calyvora.work.dto;

import com.calyvora.work.Sprint;

public record SprintResponse(
        String id,
        String projectId,
        String name,
        String goal,
        String startDate,
        String endDate,
        String status,
        Integer capacityPoints,
        long taskCount,
        long doneCount,
        String createdAt
) {
    public static SprintResponse of(Sprint s, long taskCount, long doneCount) {
        return new SprintResponse(
                s.getId().toString(), s.getProjectId().toString(), s.getName(), s.getGoal(),
                s.getStartDate() == null ? null : s.getStartDate().toString(),
                s.getEndDate() == null ? null : s.getEndDate().toString(),
                s.getStatus().name(), s.getCapacityPoints(), taskCount, doneCount, s.getCreatedAt().toString());
    }
}
