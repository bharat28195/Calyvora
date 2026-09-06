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
        int employees,
        /**
         * Employer statutory contributions for the month — PF today, ESI and the rest later. Zero
         * when statutory payroll is switched off for the company.
         *
         * <p>Separate from gross and net because it is neither: the employer pays it on top of
         * salary and the employee never sees it in their bank account. The number a company budgets
         * against is gross + this, and there was previously nowhere to read it.
         */
        BigDecimal totalEmployerContribution
) {
    /**
     * @param employeePf deducted from this person's net, already inside {@code net}
     * @param employerContribution paid by the company on top, outside {@code gross} and {@code net}
     */
    public record Row(String employeeId, String name, String jobTitle,
                      BigDecimal gross, double lopDays, BigDecimal net,
                      BigDecimal employeePf, BigDecimal employerContribution) {
    }
}
