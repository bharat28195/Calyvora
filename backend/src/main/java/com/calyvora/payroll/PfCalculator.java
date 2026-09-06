package com.calyvora.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Provident Fund for one employee for one month.
 *
 * <p>A pure function of (PF wages, settings). No repository, no tenant, no clock — the same reasoning
 * as the leave accrual engine, and for a stronger reason: this number appears on a payslip, is
 * deducted from somebody's salary, and is filed with the EPFO. It has to be checkable by writing down
 * a wage and an expected split, and those checks have to run in milliseconds so there are dozens of
 * them rather than three.
 *
 * <p><b>The rules, as the EPF &amp; MP Act states them.</b> The employee contributes a percentage of
 * PF wages (basic + dearness allowance). The employer contributes the same percentage, but it is
 * <em>split</em>: 8.33% goes to the Pension Scheme (EPS) and the remainder to the provident fund
 * itself. Two details cause most of the errors:
 *
 * <ul>
 *   <li><b>EPS is always capped at the wage ceiling</b>, even when the employer contributes on a
 *       higher wage. A company that opts out of the ceiling for PF does not opt out of it for EPS —
 *       computing 8.33% of a ₹50,000 basic would overstate the pension share roughly fourfold and
 *       understate the EPF share by the same amount. The total is unchanged, which is exactly why it
 *       goes unnoticed until the EPFO rejects the return.</li>
 *   <li><b>The employer's EPF share is the remainder, not its own percentage.</b> Deriving it by
 *       subtraction rather than as (employer_rate − eps_rate) × wages keeps the two halves adding to
 *       the employer total to the paisa, whatever rounding does.</li>
 * </ul>
 *
 * <p>Rounded to whole rupees, which is what the EPFO's ECR file expects and what every Indian payslip
 * shows. Rounding each component and then summing (rather than summing and rounding once) is
 * deliberate: the filed return lists the components, so those are the numbers that must be internally
 * consistent.
 */
public final class PfCalculator {

    private PfCalculator() {
    }

    /**
     * The split for one month.
     *
     * @param pfWages          the wages PF was computed on, after any ceiling
     * @param employee         deducted from the employee's salary
     * @param employerEps      the employer's share going to the Pension Scheme
     * @param employerEpf      the employer's share going to the provident fund
     * @param adminCharges     employer-only, not deducted from anybody
     * @param edli             employer-only, the linked insurance premium
     * @param employerTotal    EPS + EPF + admin + EDLI — the real cost to the company
     */
    public record Result(BigDecimal pfWages, BigDecimal employee, BigDecimal employerEps,
                         BigDecimal employerEpf, BigDecimal adminCharges, BigDecimal edli,
                         BigDecimal employerTotal) {

        public static final Result NONE = new Result(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * @param basicPlusDa the month's PF wages before any ceiling — basic plus dearness allowance,
     *                    <em>not</em> gross. Passing gross here is the single most expensive mistake
     *                    available in this file, so the parameter is named for what it must be.
     */
    public static Result compute(BigDecimal basicPlusDa, PfSettings settings) {
        if (basicPlusDa == null || basicPlusDa.signum() <= 0) {
            return Result.NONE;
        }
        BigDecimal ceiling = settings.getWageCeiling();

        // What the contribution is computed on: capped, or the real wage if the company opted out.
        BigDecimal contributionWages = settings.isRestrictToCeiling()
                ? basicPlusDa.min(ceiling)
                : basicPlusDa;

        // EPS is capped at the ceiling regardless of the above. See the class note.
        BigDecimal epsWages = basicPlusDa.min(ceiling);

        BigDecimal employee = pct(contributionWages, settings.getEmployeeRate());
        BigDecimal employerTotalPf = pct(contributionWages, settings.getEmployerRate());
        BigDecimal eps = pct(epsWages, settings.getEpsRate());

        // The remainder, so the two halves always add back to the employer's contribution exactly.
        // A negative is arithmetically possible if somebody sets eps_rate above employer_rate; the
        // database CHECK prevents it, and clamping here means a bad row cannot produce a credit.
        BigDecimal epf = employerTotalPf.subtract(eps).max(BigDecimal.ZERO);

        BigDecimal admin = pct(contributionWages, settings.getAdminChargeRate());
        BigDecimal edli = pct(epsWages, settings.getEdliRate());

        return new Result(contributionWages, employee, eps, epf, admin, edli,
                eps.add(epf).add(admin).add(edli));
    }

    /** {@code rate}% of {@code amount}, to whole rupees. */
    private static BigDecimal pct(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
