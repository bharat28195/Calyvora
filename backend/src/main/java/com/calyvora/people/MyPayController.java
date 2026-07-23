package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.CompensationResponse;
import com.calyvora.people.dto.PayslipResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service pay: an employee sees their <em>own</em> salary and payslip (a core HR feature —
 * people expect to view their own payslips). Distinct from {@link CompensationController}, which is
 * Owner/Admin and can see anyone's; here the data is always the caller's own.
 */
@RestController
@RequestMapping("/api/v1/people/me")
public class MyPayController {

    private final CompensationService compensationService;

    public MyPayController(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @GetMapping("/compensation")
    public CompensationResponse myCompensation(@CurrentUser AuthPrincipal principal) {
        return compensationService.forSelf(principal.userId());
    }

    @GetMapping("/payslip")
    public PayslipResponse myPayslip(@CurrentUser AuthPrincipal principal,
                                     @RequestParam(name = "month", required = false) String month) {
        return compensationService.payslipForSelf(principal.userId(), month);
    }
}
