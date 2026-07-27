package com.calyvora.people.dto;

import java.math.BigDecimal;
import java.util.List;

/** A month's payroll run — every employee's computed net (after attendance LOP), plus totals. */
public record PayrollRunResponse(
        String month,
        String currency,
        List<Row> rows,
        BigDecimal totalGross,
        BigDecimal totalNet,
        double totalLopDays,
        int employees
) {
    public record Row(String employeeId, String name, String jobTitle,
                      BigDecimal gross, double lopDays, BigDecimal net) {
    }
}
