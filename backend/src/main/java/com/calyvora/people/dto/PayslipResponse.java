package com.calyvora.people.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A generated monthly payslip (feedback C3), computed from the employee's current salary with a
 * simple, transparent breakdown. Not persisted — derived on demand for any month.
 *
 * <p>Carries everything the printed payslip shows, so the frontend renders a document rather than
 * assembling one from four calls: the company's branding block, who the employee is, the statutory
 * identifiers a payslip is legally expected to carry (UAN, PF number, PAN), and the amounts.
 */
public record PayslipResponse(
        String employeeId,
        String employeeName,
        String month,          // YYYY-MM
        String currency,

        // --- company header ---
        String companyName,    // legal name, falling back to the company name
        String companyAddress,
        String companyLogoUrl,

        // --- who this is for ---
        String employeeNo,
        String dateJoined,
        String department,
        String designation,

        // --- statutory identifiers printed on a payslip ---
        String paymentMode,
        String uan,
        String pfNumber,
        String panMasked,

        // --- amounts ---
        List<Line> earnings,
        List<Line> deductions,
        BigDecimal gross,
        BigDecimal totalDeductions,
        BigDecimal net,
        /** The net amount spelled out, as a payslip is conventionally required to show it. */
        String netInWords,

        // Attendance linkage: LOP (unpaid absence) reduces net pay for the month.
        int workingDays,
        double lopDays,
        double payableDays
) {
    public record Line(String label, BigDecimal amount) {}
}
