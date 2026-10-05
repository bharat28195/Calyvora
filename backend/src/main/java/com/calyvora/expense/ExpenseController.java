package com.calyvora.expense;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.expense.dto.ExpensePayload;
import com.calyvora.expense.dto.ExpenseResponse;
import com.calyvora.expense.dto.ExpenseSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Expense claims. Anyone can submit and manage their own; approving, rejecting and marking
 * reimbursed is Owner/Admin.
 */
@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    // ---- mine ----

    @GetMapping("/me")
    public ExpenseSummaryResponse mine(@CurrentUser AuthPrincipal principal,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(required = false) Integer size) {
        return expenseService.mine(principal, status, cursor, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse submit(@Valid @RequestBody ExpensePayload payload,
                                  @CurrentUser AuthPrincipal principal) {
        return expenseService.submit(payload, principal);
    }

    @PatchMapping("/{claimId}")
    public ExpenseResponse update(@PathVariable UUID claimId, @Valid @RequestBody ExpensePayload payload,
                                  @CurrentUser AuthPrincipal principal) {
        return expenseService.update(claimId, payload, principal);
    }

    @DeleteMapping("/{claimId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID claimId, @CurrentUser AuthPrincipal principal) {
        expenseService.withdraw(claimId, principal);
    }

    // ---- approving ----

    // HR too (founder decision, 2026-10-05): HR runs payroll, and the reimbursement queue is part of
    // the same monthly close.
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ExpenseSummaryResponse all(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Integer size) {
        return expenseService.all(status, cursor, size);
    }

    // No @PreAuthorize on approve/reject, and that is the change rather than an omission. A role can
    // say "a lead may decide expenses"; it can never say "this lead may decide THIS claim", so
    // gating by role would have to open the endpoint to every lead in the company at once. The
    // reporting tree answers the second question, and ExpenseService.requireCanDecide asks it —
    // the same shape as leave and regularization (PD-32).
    @PostMapping("/{claimId}/approve")
    public ExpenseResponse approve(@PathVariable UUID claimId,
                                   @RequestBody(required = false) Map<String, String> body,
                                   @CurrentUser AuthPrincipal principal) {
        return expenseService.decide(claimId, true, body == null ? null : body.get("note"), principal);
    }

    @PostMapping("/{claimId}/reject")
    public ExpenseResponse reject(@PathVariable UUID claimId,
                                  @RequestBody(required = false) Map<String, String> body,
                                  @CurrentUser AuthPrincipal principal) {
        return expenseService.decide(claimId, false, body == null ? null : body.get("note"), principal);
    }

    // Reimbursement is for Owner/Admin and HR (founder decision, 2026-10-05: HR manages expenses).
    // Approving a claim is still the manager's judgement about whether the spend was legitimate;
    // paying it is the finance step, which in a company this size is HR's alongside payroll.
    @PostMapping("/{claimId}/reimburse")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public ExpenseResponse reimburse(@PathVariable UUID claimId, @CurrentUser AuthPrincipal principal) {
        return expenseService.reimburse(claimId, principal);
    }
}
