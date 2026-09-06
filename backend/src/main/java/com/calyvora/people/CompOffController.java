package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.CompOffPayload;
import com.calyvora.people.dto.CompOffResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Comp-off: claim a day worked, get it approved, spend it as leave.
 *
 * <p>Anyone may claim; deciding is restricted the same way leave is, and <em>which</em> claims a
 * manager may decide is enforced in the service, because a role expression cannot say "and only for
 * their own reports".
 */
@RestController
@RequestMapping("/api/v1/people/comp-off")
public class CompOffController {

    private final CompOffService service;

    public CompOffController(CompOffService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CompOffResponse> claim(@Valid @RequestBody CompOffPayload payload,
                                                 @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.claim(principal, payload));
    }

    @GetMapping("/mine")
    public List<CompOffResponse> mine(@CurrentUser AuthPrincipal principal) {
        return service.mine(principal);
    }

    /** Claims this caller can act on: their reports', or the whole company for HR and admins. */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR','MANAGER')")
    public List<CompOffResponse> pending(@CurrentUser AuthPrincipal principal) {
        return service.pending(principal);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR','MANAGER')")
    public CompOffResponse approve(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return service.decide(id, true, principal);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR','MANAGER')")
    public CompOffResponse reject(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return service.decide(id, false, principal);
    }
}
