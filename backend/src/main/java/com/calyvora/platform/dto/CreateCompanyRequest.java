package com.calyvora.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Provision a new customer company plus its first ADMIN, and set the seat plan.
 *
 * <p>Used by both consoles. {@code agencyId} is optional and only meaningful to the platform owner:
 * null means a company sold direct — which is most of them — and an id files it under that agency
 * instead. The agency console ignores the field and always uses its own id (PD-18).
 */
public record CreateCompanyRequest(
        @NotBlank @Size(max = 120) String companyName,
        @NotBlank @Size(max = 80) String adminFirstName,
        @NotBlank @Size(max = 80) String adminLastName,
        @NotBlank @Email @Size(max = 255) String adminEmail,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Positive int seats,
        @Positive int months,
        String agencyId
) {
}
