package com.calyvora.people.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Replace the whole payslip template with this ordered list of components. */
public record SavePayslipTemplateRequest(
        @NotEmpty @Valid List<PayslipComponentPayload> components
) {
}
