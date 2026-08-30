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
        @Positive int months,
        /**
         * What this customer will be billed in — it decides which published price list applies. Set at
         * approval rather than taken from the enquiry, because the form on the website is filled in by
         * someone who has no idea what we bill in, and a currency is a commercial term, not a
         * preference. Null means rupees.
         */
        @Size(min = 3, max = 3) String currency
) {
}
