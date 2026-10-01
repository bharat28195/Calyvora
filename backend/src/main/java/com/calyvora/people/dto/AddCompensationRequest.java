package com.calyvora.people.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Record a new salary (raise or adjustment) for an employee. */
public record AddCompensationRequest(
        @NotNull @Positive BigDecimal annualAmount,
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "effective date must be YYYY-MM-DD") String effectiveDate,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 500) String reason
) {}
