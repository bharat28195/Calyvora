package com.calyvora.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** One component in a payslip-template save. Deeper rules (ranges, remainder, basis) are checked in the service. */
public record PayslipComponentPayload(
        @NotBlank @Size(max = 60) String name,
        @NotBlank String kind,   // EARNING | DEDUCTION
        @NotBlank String calc,   // PERCENT_OF_GROSS | PERCENT_OF_BASIC | FIXED | REMAINDER
        BigDecimal value,
        boolean basis
) {
}
