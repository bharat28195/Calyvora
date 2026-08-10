package com.calyvora.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The platform owner sets up an agency and the person who runs it. Only the vendor can do this — an
 * agency cannot create another agency, and nobody can sign up as one (PD-18).
 */
public record CreateAgencyRequest(
        @NotBlank @Size(max = 120) String agencyName,
        @NotBlank @Size(max = 80) String ownerFirstName,
        @NotBlank @Size(max = 80) String ownerLastName,
        @NotBlank @Email @Size(max = 255) String ownerEmail,
        @NotBlank @Size(min = 8, max = 100) String password
) {
}
