package com.calyvora.performance.dto;

import com.calyvora.people.dto.GoalResponse;
import com.calyvora.performance.PerformanceReview;

import java.math.BigDecimal;
import java.util.List;

/**
 * The full review for one employee, plus the context a rater needs in the same view: their current
 * salary (so the hike is grounded) and a rollup of their goals for the period (achieved / total).
 */
public record PerformanceReviewResponse(
        String id,
        String cycleId,
        String cycleName,
        String periodStart,
        String periodEnd,
        String cycleStatus,
        String employeeId,
        String employeeName,
        String jobTitle,
        String managerId,
        String managerName,
        String status,
        String selfAssessment,
        String selfSubmittedAt,
        Integer rating,
        String summary,
        String strengths,
        String improvements,
        String hikeType,
        BigDecimal hikePercent,
        BigDecimal proposedSalary,
        String hikeNote,
        String managerSubmittedAt,
        String decidedAt,
        // Context for the rater:
        String currency,
        BigDecimal currentSalary,
        int goalsAchieved,
        int goalsTotal,
        List<GoalResponse> goals
) {
    public static PerformanceReviewResponse of(
            PerformanceReview r, String cycleName, String periodStart, String periodEnd, String cycleStatus,
            String employeeName, String jobTitle, String managerName,
            String currency, BigDecimal currentSalary, int goalsAchieved, int goalsTotal, List<GoalResponse> goals) {
        return new PerformanceReviewResponse(
                r.getId().toString(), r.getCycleId().toString(), cycleName, periodStart, periodEnd, cycleStatus,
                r.getEmployeeId().toString(), employeeName, jobTitle,
                r.getManagerId() == null ? null : r.getManagerId().toString(), managerName,
                r.getStatus().name(),
                r.getSelfAssessment(), r.getSelfSubmittedAt() == null ? null : r.getSelfSubmittedAt().toString(),
                r.getManagerRating(), r.getManagerSummary(), r.getStrengths(), r.getImprovements(),
                r.getHikeType() == null ? null : r.getHikeType().name(),
                r.getHikePercent(), r.getProposedSalary(), r.getHikeNote(),
                r.getManagerSubmittedAt() == null ? null : r.getManagerSubmittedAt().toString(),
                r.getDecidedAt() == null ? null : r.getDecidedAt().toString(),
                currency, currentSalary, goalsAchieved, goalsTotal, goals);
    }
}
