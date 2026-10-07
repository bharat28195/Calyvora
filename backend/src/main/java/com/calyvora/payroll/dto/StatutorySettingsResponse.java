package com.calyvora.payroll.dto;

import com.calyvora.payroll.StatutorySettings;

import java.math.BigDecimal;

/**
 * A company's ESI / professional-tax settings and registration numbers.
 *
 * @param statutoryEnabled whether the vendor has switched statutory payroll on for this company at
 *                         all — without it neither switch below has any effect, and the screen says so
 */
public record StatutorySettingsResponse(
        boolean statutoryEnabled,
        boolean esiEnabled,
        BigDecimal esiEmployeeRate,
        BigDecimal esiEmployerRate,
        BigDecimal esiWageCeiling,
        boolean ptEnabled,
        String pfEstablishmentCode,
        String esiEmployerCode,
        String tan,
        String companyPan,
        String ptRegistrationNo
) {
    public static StatutorySettingsResponse of(StatutorySettings s, boolean statutoryEnabled) {
        return new StatutorySettingsResponse(statutoryEnabled, s.isEsiEnabled(), s.getEsiEmployeeRate(),
                s.getEsiEmployerRate(), s.getEsiWageCeiling(), s.isPtEnabled(), s.getPfEstablishmentCode(),
                s.getEsiEmployerCode(), s.getTan(), s.getCompanyPan(), s.getPtRegistrationNo());
    }
}
