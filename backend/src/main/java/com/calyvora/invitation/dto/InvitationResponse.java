package com.calyvora.invitation.dto;

import com.calyvora.invitation.Invitation;

public record InvitationResponse(String id, String email, String role, String status,
                                 String invitedByEmail, String createdAt, String expiresAt) {

    public static InvitationResponse of(Invitation inv, String invitedByEmail) {
        return new InvitationResponse(
                inv.getId().toString(), inv.getEmail(), inv.getRole().name(), inv.getStatus().name(),
                invitedByEmail, inv.getCreatedAt().toString(), inv.getExpiresAt().toString());
    }
}
