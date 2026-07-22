package com.calyvora.work.dto;

import jakarta.validation.constraints.Size;

/** All optional. Empty string on a date clears it. */
public record UpdateSprintRequest(
        @Size(min = 1, max = 120) String name,
        @Size(max = 500) String goal,
        String startDate,
        String endDate,
        /** What the team believes it can take on this sprint. */
        Integer capacityPoints
) {
}
