package com.calyvora.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSettingsRequest(
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Pattern(regexp = "en|en-GB|fr|de|es|hi", message = "unsupported locale") String locale,
        @NotBlank @Pattern(regexp = "INR|USD|EUR|GBP|AED|SGD|AUD|CAD", message = "unsupported currency") String currency,
        @Size(max = 500)
        @Pattern(regexp = "^$|^https://.*", message = "must be an https URL")
        String logoUrl
) {
}
