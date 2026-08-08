package com.calyvora.invitation;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.invitation.dto.AcceptInvitationRequest;
import com.calyvora.invitation.dto.CreateInvitationRequest;
import com.calyvora.invitation.dto.InvitationPreviewResponse;
import com.calyvora.invitation.dto.InvitationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
@Validated
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<InvitationResponse> create(@Valid @RequestBody CreateInvitationRequest request,
                                                     @CurrentUser AuthPrincipal principal) {
        InvitationResponse response = invitationService.create(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<InvitationResponse> list() {
        return invitationService.listPending();
    }

    /**
     * A fresh joining link for a pending invitation, so onboarding never depends on the email having
     * arrived. Invalidates the previous link — which is also what you want if it went astray.
     */
    @PostMapping("/{id}/link")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public InvitationResponse regenerateLink(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return invitationService.regenerateLink(id, principal);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        invitationService.revoke(id);
        return ResponseEntity.noContent().build();
    }

    /** Public: preview an invitation before accepting (shows company + email + role). */
    @GetMapping("/preview")
    public InvitationPreviewResponse preview(@RequestParam @NotBlank String token) {
        return invitationService.preview(token);
    }

    /** Public: accept an invitation, creating an active member. */
    @PostMapping("/accept")
    public ResponseEntity<Void> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        invitationService.accept(request);
        return ResponseEntity.ok().build();
    }
}
