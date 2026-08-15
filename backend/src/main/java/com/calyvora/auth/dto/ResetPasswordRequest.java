package com.calyvora.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Spend a one-time code and set a new password.
 *
 * <p>The password rule is stated here rather than left to the client, because this endpoint is a
 * second front door: everything reachable from the sign-in screen has to hold the same line, or the
 * weakest route decides how strong passwords actually are.
 */
public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "The code is 6 digits") String code,
        @NotBlank
        @Size(min = 10, max = 100, message = "Use at least 10 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Include at least one letter")
        @Pattern(regexp = ".*\\d.*", message = "Include at least one number")
        String newPassword
) {
}
