package com.calyvora.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create/update a client. On create, {@code name} is required (enforced in the service). */
public record ClientPayload(
        @Size(max = 160) String name,
        @Size(max = 160) String contactName,
        @Email @Size(max = 200) String contactEmail,
        @Size(max = 40) String phone,
        @Size(max = 200) String website,
        @Pattern(regexp = "LEAD|ACTIVE|CHURNED", message = "invalid status") String status,
        @Size(max = 4000) String notes
) {}
