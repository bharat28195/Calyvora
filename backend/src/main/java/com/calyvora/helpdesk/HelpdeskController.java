package com.calyvora.helpdesk;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.helpdesk.dto.CommentPayload;
import com.calyvora.helpdesk.dto.CommentResponse;
import com.calyvora.helpdesk.dto.RaiseTicketRequest;
import com.calyvora.helpdesk.dto.TicketResponse;
import com.calyvora.helpdesk.dto.UpdateTicketRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** HR Helpdesk. Employees raise/track their own tickets; HR agents (ADMIN/HR) run the queue. */
@RestController
@RequestMapping("/api/v1/helpdesk")
public class HelpdeskController {

    private final HelpdeskService service;

    public HelpdeskController(HelpdeskService service) {
        this.service = service;
    }

    // ---- employee (any authenticated user) ----

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse raise(@Valid @RequestBody RaiseTicketRequest req, @CurrentUser AuthPrincipal principal) {
        return service.raise(req, principal);
    }

    @GetMapping("/tickets/mine")
    public List<TicketResponse> mine(@CurrentUser AuthPrincipal principal) {
        return service.myTickets(principal);
    }

    @GetMapping("/tickets/{id}")
    public TicketResponse ticket(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return service.ticket(id, principal);
    }

    @GetMapping("/tickets/{id}/comments")
    public List<CommentResponse> comments(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return service.comments(id, principal);
    }

    @PostMapping("/tickets/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse comment(@PathVariable UUID id, @Valid @RequestBody CommentPayload payload,
                                   @CurrentUser AuthPrincipal principal) {
        return service.addComment(id, payload, principal);
    }

    // ---- HR agents (ADMIN/HR) ----

    @GetMapping("/tickets")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public List<TicketResponse> queue(@RequestParam(required = false) String status) {
        return service.queue(status);
    }

    @PatchMapping("/tickets/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public TicketResponse update(@PathVariable UUID id, @RequestBody UpdateTicketRequest req,
                                 @CurrentUser AuthPrincipal principal) {
        return service.update(id, req, principal);
    }
}
