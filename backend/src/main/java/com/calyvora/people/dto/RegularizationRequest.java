package com.calyvora.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An employee raises a regularization for a missed/incorrect day. Times are ISO {@code HH:mm}. */
public record RegularizationRequest(
        @NotBlank String date,     // YYYY-MM-DD
        String checkIn,
        String checkOut,
        @Size(max = 500) String reason
) {
}
