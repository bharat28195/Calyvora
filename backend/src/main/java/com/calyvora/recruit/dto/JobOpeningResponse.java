package com.calyvora.recruit.dto;

import com.calyvora.recruit.JobOpening;

/** A job opening with its pipeline totals for the list view. */
public record JobOpeningResponse(
        String id,
        String title,
        String departmentId,
        String department,
        String location,
        String employmentType,
        String description,
        int positions,
        String status,
        long candidateCount,
        long hiredCount,
        String createdAt
) {
    public static JobOpeningResponse of(JobOpening j, String departmentName, long candidateCount, long hiredCount) {
        return new JobOpeningResponse(
                j.getId().toString(), j.getTitle(),
                j.getDepartmentId() == null ? null : j.getDepartmentId().toString(), departmentName,
                j.getLocation(), j.getEmploymentType(), j.getDescription(), j.getPositions(),
                j.getStatus().name(), candidateCount, hiredCount, j.getCreatedAt().toString());
    }
}
