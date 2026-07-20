package com.calyvora.work;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.work.dto.CreateTicketRequest;
import com.calyvora.work.dto.TicketResponse;
import com.calyvora.work.dto.UpdateTicketRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Work OS — support tickets (slice S3). */
@RestController
@RequestMapping("/api/v1/work")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/projects/{projectId}/tickets")
    public List<TicketResponse> listForProject(@PathVariable UUID projectId) {
        return ticketService.listForProject(projectId);
    }

    @PostMapping("/projects/{projectId}/tickets")
    public ResponseEntity<TicketResponse> create(@PathVariable UUID projectId,
                                                 @Valid @RequestBody CreateTicketRequest request,
                                                 @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.create(projectId, request, principal));
    }

    @PatchMapping("/tickets/{id}")
    public TicketResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.update(id, request);
    }

    @DeleteMapping("/tickets/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ticketService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
