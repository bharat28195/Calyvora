package com.calyvora.payroll.dto;

import com.calyvora.payroll.PfSettings;

import java.math.BigDecimal;

/**
 * A company's PF rates, plus whether statutory payroll is switched on for it at all.
 *
 * @param enabled carried here so the settings screen can say "these rates are configured but not in
 *                use" rather than implying deductions are happening when they are not. The switch is
 *                the vendor's to flip, not the customer's, so the screen must explain it rather than
 *                offer it.
 */
public record PfSettingsResponse(
        boolean enabled,
        BigDecimal wageCeiling,
        boolean restrictToCeiling,
        BigDecimal employeeRate,
        BigDecimal employerRate,
        BigDecimal epsRate,
        BigDecimal adminChargeRate,
        BigDecimal edliRate
) {
    public static PfSettingsResponse of(PfSettings s, boolean enabled) {
        return new PfSettingsResponse(enabled, s.getWageCeiling(), s.isRestrictToCeiling(),
                s.getEmployeeRate(), s.getEmployerRate(), s.getEpsRate(),
                s.getAdminChargeRate(), s.getEdliRate());
    }
}
