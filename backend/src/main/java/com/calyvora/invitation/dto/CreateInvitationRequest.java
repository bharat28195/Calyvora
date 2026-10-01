package com.calyvora.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admins invite into the company ladder — ADMIN, HR, MANAGER or MEMBER — never an OWNER (OWNER is
 * the platform vendor, PD-10). */
public record CreateInvitationRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "ADMIN|HR|MANAGER|MEMBER", message = "must be ADMIN, HR, MANAGER or MEMBER") String role
) {
}
