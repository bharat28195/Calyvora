package com.calyvora.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Change your own password while signed in. The new password follows the same rule as a reset —
 * every route to a password holds the same line.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Size(min = 10, max = 100, message = "Use at least 10 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Include at least one letter")
        @Pattern(regexp = ".*\\d.*", message = "Include at least one number")
        String newPassword
) {
}
