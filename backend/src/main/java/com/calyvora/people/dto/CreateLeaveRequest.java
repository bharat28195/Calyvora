package com.calyvora.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLeaveRequest(
        // COMP_OFF is here but is not like the others: it is spent from earned credits rather than
        // from an annual entitlement, so the service checks credits before accepting one.
        @NotBlank @Pattern(regexp = "VACATION|SICK|PERSONAL|UNPAID|COMP_OFF", message = "invalid leave type") String type,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "start date must be YYYY-MM-DD") String startDate,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "end date must be YYYY-MM-DD") String endDate,
        @Size(max = 500) String reason
) {
}
