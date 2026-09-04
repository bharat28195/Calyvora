package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.CreateLeaveRequest;
import com.calyvora.people.dto.LeaveBalanceResponse;
import com.calyvora.people.dto.LeaveRequestResponse;
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

/** People OS — time-off / leave (slice P4). */
@RestController
@RequestMapping("/api/v1/people/leave")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    /** Submit a leave request (any member). */
    @PostMapping
    public ResponseEntity<LeaveRequestResponse> request(@Valid @RequestBody CreateLeaveRequest dto,
                                                        @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveService.request(principal, dto));
    }

    @GetMapping("/mine")
    public List<LeaveRequestResponse> mine(@CurrentUser AuthPrincipal principal) {
        return leaveService.listMine(principal);
    }

    @GetMapping("/balance")
    public LeaveBalanceResponse balance(@CurrentUser AuthPrincipal principal) {
        return leaveService.balance(principal);
    }

    /**
     * Approvals inbox — the whole company for HR and admins, a manager's own reports for a manager.
     *
     * <p>MANAGER is on this list, and on approve/reject below, because a manager who cannot decide
     * their team's leave is not a manager: every holiday in the company funnelled through HR. The role
     * check only decides who may reach the endpoint at all; <em>which</em> requests a manager may see
     * and decide is enforced in {@link LeaveService}, because Spring's role expressions cannot express
     * "and only for their own reports".
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR','MANAGER')")
    public List<LeaveRequestResponse> all(@CurrentUser AuthPrincipal principal) {
        return leaveService.listForApprover(principal);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR','MANAGER')")
    public LeaveRequestResponse approve(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return leaveService.approve(id, principal);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR','MANAGER')")
    public LeaveRequestResponse reject(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return leaveService.reject(id, principal);
    }

    @PostMapping("/{id}/cancel")
    public LeaveRequestResponse cancel(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return leaveService.cancel(id, principal);
    }
}
