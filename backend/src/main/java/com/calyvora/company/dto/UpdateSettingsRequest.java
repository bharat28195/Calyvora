package com.calyvora.company.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSettingsRequest(
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Pattern(regexp = "en|en-GB|fr|de|es|hi", message = "unsupported locale") String locale,
        @NotBlank @Pattern(regexp = "INR|USD|EUR|GBP|AED|SGD|AUD|CAD", message = "unsupported currency") String currency,
        @Size(max = 160) String legalName,
        @Size(max = 300) String address,
        @Size(max = 500)
        @Pattern(regexp = "^$|^https://.*", message = "must be an https URL")
        String logoUrl,

        /**
         * Minutes of inactivity before a session ends. Zero turns the policy off; omitting the
         * field leaves it alone, like every other optional field on this request.
         *
         * <p>Floored at five because anything shorter signs people out mid-form, and capped at a
         * week because "off" already has a spelling and a 60-day idle window is that answer
         * pretending to be a policy.
         */
        @Min(value = 0, message = "must be 0 (never) or between 5 and 10080 minutes")
        @Max(value = 10080, message = "must be at most 7 days (10080 minutes)")
        Integer sessionIdleMinutes
) {
}
