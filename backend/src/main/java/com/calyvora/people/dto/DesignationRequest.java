package com.calyvora.people.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create or rename a rung.
 *
 * @param level ascending, sparse by convention (0, 10, 20) so one can be slotted between two others.
 *              Bounded rather than unbounded: the number is only ever an ordering key, and an
 *              unbounded one invites somebody to type a year into it.
 */
public record DesignationRequest(
        @NotBlank(message = "Give the designation a name") @Size(max = 120) String name,
        @Min(0) @Max(1000) int level,
        boolean archived
) {
}
