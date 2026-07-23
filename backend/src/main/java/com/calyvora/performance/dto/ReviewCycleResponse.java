package com.calyvora.performance.dto;

import com.calyvora.performance.ReviewCycle;

/** A cycle with a small progress rollup for the admin list (how far along the reviews are). */
public record ReviewCycleResponse(
        String id,
        String name,
        String periodStart,
        String periodEnd,
        String status,
        int reviewCount,
        int submittedCount,
        int approvedCount,
        String createdAt
) {
    public static ReviewCycleResponse of(ReviewCycle c, int reviewCount, int submittedCount, int approvedCount) {
        return new ReviewCycleResponse(
                c.getId().toString(), c.getName(),
                c.getPeriodStart().toString(), c.getPeriodEnd().toString(),
                c.getStatus().name(), reviewCount, submittedCount, approvedCount,
                c.getCreatedAt().toString());
    }
}
