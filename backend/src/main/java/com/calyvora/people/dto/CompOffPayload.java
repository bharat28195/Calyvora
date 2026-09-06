package com.calyvora.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Claim a comp-off for a day worked that was not owed. */
public record CompOffPayload(
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date must be YYYY-MM-DD")
        String workedOn,
        @Size(max = 300) String reason
) {
}
