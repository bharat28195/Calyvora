package com.calyvora.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** The owner provisions a new customer company plus its first ADMIN, and sets the seat plan. */
public record CreateCompanyRequest(
        @NotBlank @Size(max = 120) String companyName,
        @NotBlank @Size(max = 80) String adminFirstName,
        @NotBlank @Size(max = 80) String adminLastName,
        @NotBlank @Email @Size(max = 255) String adminEmail,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Positive int seats,
        @Positive int months
) {
}
