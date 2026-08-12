package com.calyvora.trial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The terms the vendor grants when approving a trial: the starting password for the customer's
 * admin, how many seats, and how long the trial runs.
 *
 * <p>The password is typed by the owner rather than generated and emailed, matching how "New company"
 * already works — the credential is handed over by the person who sold the trial, and never travels
 * in a message we don't control.
 */
public record ApproveTrialRequest(
        @NotBlank @Size(min = 8, max = 100) String password,
        @Positive int seats,
        @Positive int months
) {
}
