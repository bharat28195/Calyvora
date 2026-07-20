package com.calyvora.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 1, max = 80) String firstName,
        @NotBlank @Size(min = 1, max = 80) String lastName,
        @NotBlank
        @Size(min = 10, max = 200)
        @Pattern(regexp = ".*[A-Za-z].*", message = "must contain a letter")
        @Pattern(regexp = ".*[0-9].*", message = "must contain a number")
        String password
) {
}
