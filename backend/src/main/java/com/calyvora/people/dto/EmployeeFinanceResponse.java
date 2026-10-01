package com.calyvora.people.dto;

import com.calyvora.people.EmployeeFinance;

/**
 * An employee's "My Finances" record: how they're paid, what they're enrolled in, and the identity
 * used on statutory filings.
 *
 * <p>The account number and PAN are returned <em>masked</em> — the screen only ever needs enough to
 * recognise which account it is, so the full value doesn't need to travel to the browser at all.
 * {@code panMasked} keeps the last four characters, the way every Indian payroll portal shows it.
 */
public record EmployeeFinanceResponse(
        String employeeId,
        String employeeName,
        // --- payment ---
        String paymentMode,
        String bankName,
        String bankAccountMasked,
        String bankIfsc,
        String bankAccountName,
        String bankBranch,
        // --- provident fund ---
        String pfStatus,
        String pfNumber,
        String uan,
        String pfJoinDate,
        String pfAccountName,
        // --- ESI ---
        String esiStatus,
        String esiNumber,
        // --- professional tax ---
        String ptState,
        String ptLocation,
        // --- identity ---
        String panMasked,
        boolean panVerified,
        String dateOfBirth,
        String parentName
) {

    public static EmployeeFinanceResponse of(EmployeeFinance f, String employeeName) {
        return new EmployeeFinanceResponse(
                f.getEmployeeId().toString(),
                employeeName,
                f.getPaymentMode(),
                f.getBankName(),
                maskAccount(f.getBankAccountNo()),
                f.getBankIfsc(),
                f.getBankAccountName(),
                f.getBankBranch(),
                f.getPfStatus(),
                f.getPfNumber(),
                f.getUan(),
                f.getPfJoinDate() == null ? null : f.getPfJoinDate().toString(),
                f.getPfAccountName(),
                f.getEsiStatus(),
                f.getEsiNumber(),
                f.getPtState(),
                f.getPtLocation(),
                maskPan(f.getPanNumber()),
                f.isPanVerified(),
                f.getDateOfBirth() == null ? null : f.getDateOfBirth().toString(),
                f.getParentName());
    }

    /** Last four digits only — enough to tell two accounts apart, useless to anyone else. */
    private static String maskAccount(String account) {
        return mask(account, 4);
    }

    /** PAN as `XXXXXX894N`: the last four characters, which is how payroll portals display it. */
    private static String maskPan(String pan) {
        return mask(pan, 4);
    }

    private static String mask(String value, int visible) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= visible) {
            return trimmed;
        }
        return "X".repeat(trimmed.length() - visible) + trimmed.substring(trimmed.length() - visible);
    }
}
