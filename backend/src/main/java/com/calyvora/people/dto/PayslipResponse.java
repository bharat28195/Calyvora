package com.calyvora.people.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A generated monthly payslip (feedback C3), computed from the employee's current salary with a simple,
 * transparent breakdown. Not persisted — derived on demand for any month.
 */
public record PayslipResponse(
        String employeeId,
        String employeeName,
        String month,          // YYYY-MM
        String currency,
        String companyName,    // payslip header (legal name, falling back to company name)
        String companyAddress,
        List<Line> earnings,
        List<Line> deductions,
        BigDecimal gross,
        BigDecimal totalDeductions,
        BigDecimal net,
        // Attendance linkage: LOP (unpaid absence) reduces net pay for the month.
        int workingDays,
        double lopDays,
        double payableDays
) {
    public record Line(String label, BigDecimal amount) {}
}
