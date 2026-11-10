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
        double payableDays,

        /**
         * Statutory contributions, or null when the company does not have statutory payroll switched
         * on or this employee is not enrolled.
         *
         * <p>Nullable rather than a block of zeroes: a payslip showing "PF: 0" reads as an error to
         * the person holding it, where an absent section reads as "not applicable", which is the
         * truth.
         */
        Statutory statutory,

        /** Income tax (TDS) withheld this month; null when the company does not withhold it here. */
        BigDecimal incomeTax,

        /**
         * True when this payslip comes from a locked (finalised) month and will never change. False
         * for a month still open, whose figures follow today's salary, attendance and settings.
         */
        boolean finalized
) {
    public record Line(String label, BigDecimal amount) {}

    /** Gross actually paid: the month's gross less the loss-of-pay line(s). */
    public BigDecimal earnedGross() {
        BigDecimal lop = BigDecimal.ZERO;
        for (Line l : deductions) {
            if (l.label() != null && l.label().startsWith("Loss of pay")) {
                lop = lop.add(l.amount());
            }
        }
        return gross.subtract(lop);
    }

    /** The same payslip, marked as coming from a locked month. */
    public PayslipResponse asFinalized() {
        return new PayslipResponse(employeeId, employeeName, month, currency, companyName, companyAddress,
                companyLogoUrl, employeeNo, dateJoined, department, designation, paymentMode, uan, pfNumber,
                panMasked, earnings, deductions, gross, totalDeductions, net, netInWords, workingDays,
                lopDays, payableDays, statutory, incomeTax, true);
    }

    /**
     * What the employer pays on top of salary, and on what.
     *
     * <p>The employee's own PF contribution is <em>not</em> here — it is a deduction line like any
     * other, because it comes out of their pay and must be inside the net calculation. This block is
     * the employer's side, which never touches net and which the employee is nonetheless entitled to
     * see: it is their pension.
     *
     * @param pfWages the wages the contribution was computed on, after any ceiling — printed because
     *                "why is my PF 1,800 when my basic is 50,000?" is the most common payslip
     *                question in India, and the answer is this number
     */
    /**
     * Each scheme's fields are null when that scheme does not apply to this person — PF null for
     * somebody not enrolled, ESI null above the ceiling, PT null in a state without it — for the same
     * reason the whole block is nullable: "not applicable" is the truth, "0" reads as a mistake.
     *
     * @param esiWages       the gross wages ESI was computed on (after loss of pay)
     * @param employeeEsi    deducted from pay; zero (not null) for a low earner whose share is waived
     * @param employerEsi    paid by the company on top
     * @param professionalTax deducted from pay this month
     * @param ptState        two-letter code of the state the PT was computed for
     * @param employerTotal  PF and ESI employer contributions together — the company's cost on top
     */
    public record Statutory(BigDecimal pfWages, BigDecimal employeePf, BigDecimal employerEps,
                            BigDecimal employerEpf, BigDecimal employerAdminCharges,
                            BigDecimal employerEdli,
                            BigDecimal esiWages, BigDecimal employeeEsi, BigDecimal employerEsi,
                            BigDecimal professionalTax, String ptState,
                            /** Labour Welfare Fund: deducted / paid on top, only in a collection month. */
                            BigDecimal lwfEmployee, BigDecimal lwfEmployer,
                            BigDecimal employerTotal) {}
}
