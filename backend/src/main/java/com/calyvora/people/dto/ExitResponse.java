package com.calyvora.people.dto;

import java.util.List;

/**
 * The state of one person's exit: where they are, how far the clearance has got, and the letters
 * that have been issued. Everything the exit screen needs in one call.
 */
public record ExitResponse(
        String employeeId,
        String employeeName,
        String employmentStatus,
        String lastWorkingDay,
        String reason,
        String exitStartedAt,
        String managerName,
        int tasksDone,
        int tasksTotal,
        boolean checklistComplete,
        List<OnboardingTaskResponse> checklist,
        List<IssuedLetter> letters
) {
    /** A letter raised for this exit, so the screen can link to it rather than re-render it. */
    public record IssuedLetter(String id, String kind, String title, String createdAt) {}
}
