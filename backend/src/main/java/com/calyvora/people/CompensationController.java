package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.AddCompensationRequest;
import com.calyvora.people.dto.CompensationResponse;
import com.calyvora.people.dto.PayslipResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Compensation & payslips (People OS, feedback C1–C3). Sensitive — Owner/Admin only.
 */
@RestController
@RequestMapping("/api/v1/people/employees/{employeeId}")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'HR')")
public class CompensationController {

    private final CompensationService compensationService;

    public CompensationController(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @GetMapping("/compensation")
    public CompensationResponse compensation(@PathVariable UUID employeeId) {
        return compensationService.forEmployee(employeeId);
    }

    @PostMapping("/compensation")
    public CompensationResponse addCompensation(@PathVariable UUID employeeId,
                                                @Valid @RequestBody AddCompensationRequest request,
                                                @CurrentUser AuthPrincipal principal) {
        return compensationService.add(employeeId, request, principal);
    }

    @GetMapping("/payslip")
    public PayslipResponse payslip(@PathVariable UUID employeeId,
                                   @RequestParam(name = "month", required = false) String month) {
        return compensationService.payslip(employeeId, month);
    }
}
