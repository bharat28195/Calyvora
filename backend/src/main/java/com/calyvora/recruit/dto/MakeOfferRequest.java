package com.calyvora.recruit.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Make an offer to a candidate (PD-20). Everything is optional except what the letter needs, and
 * anything left out falls back to the job opening — the point is one form, not a re-typing exercise.
 */
public record MakeOfferRequest(
        @Size(max = 120, message = "Job title cannot be longer than 120 characters")
        String jobTitle,

        LocalDate startDate,

        @Size(max = 120, message = "Location cannot be longer than 120 characters")
        String workLocation,

        @Size(max = 40, message = "Employment type cannot be longer than 40 characters")
        String employmentType,

        BigDecimal annualSalary,

        @Size(max = 3, message = "Use a three-letter currency code")
        String currency,

        UUID departmentId
) {
    public MakeOfferRequest {
        jobTitle = trim(jobTitle);
        workLocation = trim(workLocation);
        employmentType = trim(employmentType);
        currency = currency == null || currency.isBlank()
                ? null : currency.trim().toUpperCase(java.util.Locale.ENGLISH);
    }

    private static String trim(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
