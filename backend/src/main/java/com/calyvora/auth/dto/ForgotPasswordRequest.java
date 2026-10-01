package com.calyvora.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "I've forgotten my password" — the address to send a one-time code to. */
public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 255) String email
) {
}
