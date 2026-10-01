package com.calyvora.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sprint1 §12 validation rules. Server is the authority; the client also validates for UX. */
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String companyName,
        @NotBlank @Size(min = 1, max = 80) String firstName,
        @NotBlank @Size(min = 1, max = 80) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank
        @Size(min = 10, max = 200)
        @Pattern(regexp = ".*[A-Za-z].*", message = "must contain a letter")
        @Pattern(regexp = ".*[0-9].*", message = "must contain a number")
        String password
) {
}
