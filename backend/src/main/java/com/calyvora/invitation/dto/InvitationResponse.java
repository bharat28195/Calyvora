package com.calyvora.invitation.dto;

import com.calyvora.invitation.Invitation;

/**
 * @param acceptUrl the joining link, returned <em>only</em> to the admin at the moment they create or
 *                  regenerate an invitation — never when listing, because the token is stored hashed
 *                  and can't be recovered afterwards.
 *
 *                  <p>Handing the link back matters: it means adding a colleague never depends on
 *                  email being deliverable. The admin can send it over WhatsApp, Slack or read it
 *                  out, and mail becomes a convenience rather than the only way in.
 */
public record InvitationResponse(String id, String email, String role, String status,
                                 String invitedByEmail, String createdAt, String expiresAt,
                                 String acceptUrl) {

    public static InvitationResponse of(Invitation inv, String invitedByEmail) {
        return of(inv, invitedByEmail, null);
    }

    public static InvitationResponse of(Invitation inv, String invitedByEmail, String acceptUrl) {
        return new InvitationResponse(
                inv.getId().toString(), inv.getEmail(), inv.getRole().name(), inv.getStatus().name(),
                invitedByEmail, inv.getCreatedAt().toString(), inv.getExpiresAt().toString(), acceptUrl);
    }
}
