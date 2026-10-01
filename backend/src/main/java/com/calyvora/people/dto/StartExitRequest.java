package com.calyvora.people.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Begin someone's exit: the last working day, and why they are leaving. */
public record StartExitRequest(
        @NotNull(message = "Last working day is required")
        LocalDate lastWorkingDay,

        @Size(max = 200, message = "Reason cannot be longer than 200 characters")
        String reason,

        /** Defaults to true — the point of the feature is that the checklist appears by itself. */
        Boolean seedChecklist
) {
    public StartExitRequest {
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public boolean shouldSeedChecklist() {
        return seedChecklist == null || seedChecklist;
    }
}
