package com.calyvora.agency.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * An agency asking the vendor for more seats on one of its companies.
 *
 * <p>Typed rather than read out of a raw map: parsing an int from an untyped body threw on anything
 * that wasn't a number, which surfaced as a 500 — a server fault for what is plainly a bad request.
 */
public record AgencySeatRequest(
        @NotNull(message = "is required") @Positive(message = "must be at least 1")
        @Max(value = 100_000, message = "must be 100000 or fewer")
        Integer seats,

        @Size(max = 300) String note
) {
}
