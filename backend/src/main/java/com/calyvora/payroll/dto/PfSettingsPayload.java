package com.calyvora.payroll.dto;

import java.math.BigDecimal;

/** Edit a company's PF rates. Every field optional — a PATCH, so one change does not resend the rest. */
public record PfSettingsPayload(
        BigDecimal wageCeiling,
        Boolean restrictToCeiling,
        BigDecimal employeeRate,
        BigDecimal employerRate,
        BigDecimal epsRate,
        BigDecimal adminChargeRate,
        BigDecimal edliRate
) {
}
