package com.calyvora.people.dto;

import com.calyvora.people.CompOffCredit;

import java.time.LocalDate;

/**
 * One comp-off credit.
 *
 * @param spendable whether it can be used today — an APPROVED credit past its expiry date is not,
 *                  and the screen must say so rather than offering a day that will be refused
 */
public record CompOffResponse(
        String id,
        String employeeId,
        String employeeName,
        String workedOn,
        String reason,
        String status,
        String expiresOn,
        boolean spendable,
        String createdAt
) {
    public static CompOffResponse of(CompOffCredit c, String employeeName, LocalDate asOf) {
        return new CompOffResponse(
                c.getId().toString(),
                c.getEmployeeId().toString(),
                employeeName,
                c.getWorkedOn().toString(),
                c.getReason(),
                c.getStatus().name(),
                c.getExpiresOn() == null ? null : c.getExpiresOn().toString(),
                c.isSpendable(asOf),
                c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
    }
}
