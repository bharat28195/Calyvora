package com.calyvora.platform.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** A company admin asks the owner for a new (higher) seat count. */
public record RequestSeatsRequest(
        @Positive int seats,
        @Size(max = 300) String note
) {
}
