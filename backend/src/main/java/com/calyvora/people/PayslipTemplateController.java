package com.calyvora.people;

import com.calyvora.people.dto.PayslipComponentResponse;
import com.calyvora.people.dto.SavePayslipTemplateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The company payslip template (Owner/Admin). Base {@code /api/v1/payroll/payslip-template}. */
@RestController
@RequestMapping("/api/v1/payroll/payslip-template")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class PayslipTemplateController {

    private final PayslipTemplateService service;

    public PayslipTemplateController(PayslipTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<PayslipComponentResponse> template() {
        return service.template();
    }

    @PutMapping
    public List<PayslipComponentResponse> save(@Valid @RequestBody SavePayslipTemplateRequest request) {
        return service.save(request.components());
    }
}
