package com.calyvora.payroll.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Edit a company's ESI / professional-tax settings and registration numbers. Every field optional —
 * a PATCH. Send an empty string to clear a registration number.
 */
public record StatutorySettingsPayload(
        Boolean esiEnabled,
        BigDecimal esiEmployeeRate,
        BigDecimal esiEmployerRate,
        BigDecimal esiWageCeiling,
        Boolean ptEnabled,
        Boolean lwfEnabled,
        @Size(max = 32) String pfEstablishmentCode,
        // ESI employer codes are 17 digits; checked loosely so a code typed with spaces is accepted.
        @Size(max = 32) String esiEmployerCode,
        // TAN: four letters, five digits, a letter (e.g. DELA12345B).
        @Pattern(regexp = "^$|^[A-Z]{4}[0-9]{5}[A-Z]$",
                message = "must be 10 characters — four letters, five digits, then a letter (e.g. DELA12345B)")
        String tan,
        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$",
                message = "must be 10 characters — five letters, four digits, then a letter (e.g. ABCDE1234F)")
        String companyPan,
        @Size(max = 48) String ptRegistrationNo
) {
    public StatutorySettingsPayload {
        tan = tan == null ? null : tan.replaceAll("\\s", "").toUpperCase();
        companyPan = companyPan == null ? null : companyPan.replaceAll("\\s", "").toUpperCase();
        pfEstablishmentCode = pfEstablishmentCode == null ? null : pfEstablishmentCode.replaceAll("\\s", "").toUpperCase();
        esiEmployerCode = esiEmployerCode == null ? null : esiEmployerCode.replaceAll("[\\s-]", "");
    }
}
