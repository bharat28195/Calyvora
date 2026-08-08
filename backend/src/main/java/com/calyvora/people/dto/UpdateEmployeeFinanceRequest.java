package com.calyvora.people.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edit an employee's finance record. Every field is optional — {@code null} means "leave as is", so
 * the bank section and the statutory section can be saved independently by the different people who
 * own them (the employee owns their bank details; HR owns PF/ESI/PT).
 *
 * <p>Send an empty string to clear a field.
 */
public record UpdateEmployeeFinanceRequest(
        @Pattern(regexp = "BANK_TRANSFER|CHEQUE|CASH",
                message = "choose Bank transfer, Cheque or Cash")
        String paymentMode,

        @Size(max = 120) String bankName,
        @Size(max = 40) String bankAccountNo,
        // Indian IFSC: four letters, a 0, then six alphanumerics. Blank clears it.
        @Pattern(regexp = "^$|^[A-Z]{4}0[A-Z0-9]{6}$",
                message = "must be 11 characters — four letters, a zero, then six letters or digits (e.g. HDFC0003939)")
        String bankIfsc,
        @Size(max = 120) String bankAccountName,
        @Size(max = 120) String bankBranch,

        @Pattern(regexp = "ENABLED|NOT_ELIGIBLE", message = "unsupported PF status")
        String pfStatus,
        @Size(max = 40) String pfNumber,
        @Pattern(regexp = "^$|^[0-9]{12}$", message = "must be exactly 12 digits")
        String uan,
        String pfJoinDate,
        @Size(max = 120) String pfAccountName,

        @Pattern(regexp = "ELIGIBLE|NOT_ELIGIBLE", message = "unsupported ESI status")
        String esiStatus,
        @Size(max = 40) String esiNumber,

        @Size(max = 60) String ptState,
        @Size(max = 60) String ptLocation,

        // PAN: five letters, four digits, a letter.
        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$",
                message = "must be 10 characters — five letters, four digits, then a letter (e.g. ABCDE1234F)")
        String panNumber,
        Boolean panVerified,
        String dateOfBirth,
        @Size(max = 120) String parentName
) {
    /**
     * Normalise the identifiers before validation runs, so the way people actually type these values
     * is accepted rather than rejected. IFSC and PAN are case-insensitive in practice and are always
     * stored upper-case; a UAN is often written in spaced groups of four. Without this, "hdfc0001234"
     * and "1001 2345 6789" failed validation for no reason the user could act on.
     */
    public UpdateEmployeeFinanceRequest {
        bankIfsc = squash(bankIfsc);
        panNumber = squash(panNumber);
        uan = uan == null ? null : uan.replaceAll("[\\s-]", "");
        paymentMode = paymentMode == null ? null : paymentMode.trim().toUpperCase();
        pfStatus = pfStatus == null ? null : pfStatus.trim().toUpperCase();
        esiStatus = esiStatus == null ? null : esiStatus.trim().toUpperCase();
    }

    /** Trim, drop internal whitespace, upper-case. Null stays null; blank stays blank (clears). */
    private static String squash(String v) {
        return v == null ? null : v.replaceAll("\\s", "").toUpperCase();
    }
}
