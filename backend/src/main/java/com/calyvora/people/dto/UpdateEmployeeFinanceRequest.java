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
        @Pattern(regexp = "BANK_TRANSFER|CHEQUE|CASH", message = "unsupported payment mode")
        String paymentMode,

        @Size(max = 120) String bankName,
        @Size(max = 40) String bankAccountNo,
        // Indian IFSC: four letters, a 0, then six alphanumerics. Blank clears it.
        @Pattern(regexp = "^$|^[A-Z]{4}0[A-Z0-9]{6}$", message = "not a valid IFSC code")
        String bankIfsc,
        @Size(max = 120) String bankAccountName,
        @Size(max = 120) String bankBranch,

        @Pattern(regexp = "ENABLED|NOT_ELIGIBLE", message = "unsupported PF status")
        String pfStatus,
        @Size(max = 40) String pfNumber,
        @Pattern(regexp = "^$|^[0-9]{12}$", message = "a UAN is 12 digits")
        String uan,
        String pfJoinDate,
        @Size(max = 120) String pfAccountName,

        @Pattern(regexp = "ELIGIBLE|NOT_ELIGIBLE", message = "unsupported ESI status")
        String esiStatus,
        @Size(max = 40) String esiNumber,

        @Size(max = 60) String ptState,
        @Size(max = 60) String ptLocation,

        // PAN: five letters, four digits, a letter.
        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$", message = "not a valid PAN")
        String panNumber,
        Boolean panVerified,
        String dateOfBirth,
        @Size(max = 120) String parentName
) {
}
