package com.calyvora.client.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create/update a client request. On create, {@code title} is required (enforced in the service). */
public record ClientRequestPayload(
        @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Pattern(regexp = "REQUESTED|IN_PROGRESS|DELIVERED|DECLINED", message = "invalid status") String status
) {}
