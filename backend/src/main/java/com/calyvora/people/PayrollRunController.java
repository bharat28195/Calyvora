package com.calyvora.people;

import com.calyvora.people.dto.PayrollRunResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HR/Admin payroll run — every employee's net for a month, after attendance LOP. */
@RestController
@RequestMapping("/api/v1/payroll")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
public class PayrollRunController {

    private final CompensationService compensationService;

    public PayrollRunController(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @GetMapping("/run")
    public PayrollRunResponse run(@RequestParam(required = false) String month) {
        return compensationService.payrollRun(month);
    }
}
