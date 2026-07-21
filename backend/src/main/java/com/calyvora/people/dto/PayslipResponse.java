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
        List<Line> earnings,
        List<Line> deductions,
        BigDecimal gross,
        BigDecimal totalDeductions,
        BigDecimal net
) {
    public record Line(String label, BigDecimal amount) {}
}
