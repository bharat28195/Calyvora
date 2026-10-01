package com.calyvora.tax.dto;

import com.calyvora.tax.TaxDeduction;
import com.calyvora.tax.TaxRegime;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The shapes the tax screens exchange.
 *
 * <p>Kept in one file because they are one conversation: a declaration goes out, comes back amended,
 * and the computation that follows explains itself in terms of the same sections.
 */
public final class TaxDtos {

    private TaxDtos() {
    }

    /** One section on the declaration form, with the ceiling so the form can show it. */
    public record DeductionOption(String key, String section, String label, BigDecimal cap,
                                  boolean allowedInNewRegime) {
        public static DeductionOption of(TaxDeduction d) {
            return new DeductionOption(d.name(), d.section(), d.label(), d.cap(),
                    d.allowedIn(TaxRegime.NEW));
        }
    }

    /** What the employee has on file for a year. */
    public record DeclarationResponse(
            String financialYear,
            TaxRegime regime,
            String status,
            String submittedAt,
            /** Section key to the amount claimed, uncapped — what they typed. */
            Map<String, BigDecimal> declared,
            /** Whether HR still accepts changes for this year. */
            boolean windowOpen,
            List<DeductionOption> options) {
    }

    /** What the employee (or HR) is saving. A null regime leaves the existing choice alone. */
    public record DeclarationPayload(TaxRegime regime, Map<String, BigDecimal> declared) {
    }

    /** One slab's row in the working shown on screen. */
    public record BandRow(BigDecimal from, BigDecimal to, BigDecimal ratePercent, BigDecimal taxable,
                          BigDecimal tax) {
    }

    /** One deduction as it was claimed and as it was allowed. */
    public record DeductionRow(String key, String section, String label,
                               BigDecimal declared, BigDecimal allowed) {
    }

    /**
     * The whole computation, and what it means for the next payslip.
     *
     * <p>{@code comparison} carries the same salary under the other regime, because the single most
     * valuable thing this screen can tell somebody is that they picked the costlier one — and it is
     * a question they otherwise answer with a spreadsheet or not at all.
     */
    public record ComputationResponse(
            String financialYear,
            TaxRegime regime,
            String currency,
            BigDecimal grossSalary,
            BigDecimal standardDeduction,
            List<DeductionRow> deductions,
            BigDecimal totalDeductions,
            BigDecimal taxableIncome,
            List<BandRow> bands,
            BigDecimal taxOnIncome,
            BigDecimal rebate,
            BigDecimal surcharge,
            BigDecimal cess,
            BigDecimal totalTax,
            BigDecimal monthlyTds,
            int monthsElapsed,
            BigDecimal deductedSoFar,
            BigDecimal remainingTax,
            /** What the next pay run should withhold, spreading what is left over the months left. */
            BigDecimal projectedNextMonth,
            RegimeComparison comparison) {
    }

    /** The same income under both sets of rules, and which one wins. */
    public record RegimeComparison(BigDecimal oldRegimeTax, BigDecimal newRegimeTax,
                                   TaxRegime cheaper, BigDecimal saving) {
    }

    /** One row of HR's list: who has declared, under what, and what it costs. */
    public record DeclarationSummaryRow(String employeeId, String employeeName, TaxRegime regime,
                                        String status, BigDecimal totalDeclared, BigDecimal annualTax) {
    }
}
