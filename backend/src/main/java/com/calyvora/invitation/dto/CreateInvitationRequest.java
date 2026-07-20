package com.calyvora.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** You can invite an ADMIN or MEMBER — never an OWNER (Sprint1 §12). */
public record CreateInvitationRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "ADMIN|MEMBER", message = "must be ADMIN or MEMBER") String role
) {
}
