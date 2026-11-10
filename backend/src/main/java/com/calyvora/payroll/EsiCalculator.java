package com.calyvora.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Employees' State Insurance for one employee for one month.
 *
 * <p>A pure function, for the same reason as {@link PfCalculator}: the number is deducted from a
 * salary and filed with the ESIC, so it has to be checkable on paper and testable by the dozen.
 *
 * <p><b>The rules, as the ESI Act and the ESIC apply them.</b>
 * <ul>
 *   <li><b>Coverage is decided per contribution period, not per month.</b> There are two periods,
 *       April–September and October–March. Someone whose gross wages were at or under the ceiling
 *       (21,000) when the period began stays covered until it ends, even if a raise takes them over
 *       the ceiling halfway through. Deciding month by month — the obvious implementation — stops
 *       contributions the month after an increment, and the ESIC treats that as short payment.</li>
 *   <li><b>Contributions are on wages actually paid</b> in the month: gross after loss of pay, not the
 *       contracted figure.</li>
 *   <li><b>Low earners pay no employee share.</b> An employee whose average daily wage is 176 or less
 *       is exempt from the 0.75%; the employer still pays its 3.25%.</li>
 *   <li><b>Rounded up to the next whole rupee</b>, each share separately — the ESIC's own rounding,
 *       and the reason a calculator that rounds half-up disagrees with the portal by a rupee on some
 *       rows of every return.</li>
 * </ul>
 */
public final class EsiCalculator {

    /** Average daily wage at or below which the employee's own share is waived. */
    public static final BigDecimal LOW_WAGE_DAILY_LIMIT = new BigDecimal("176");

    private EsiCalculator() {
    }

    /**
     * @param covered  whether this employee is inside ESI for the contribution period at all
     * @param wages    the ESI wages for the month (zero when not covered)
     * @param employee deducted from the employee's pay
     * @param employer paid by the company on top
     */
    public record Result(boolean covered, BigDecimal wages, BigDecimal employee, BigDecimal employer) {
        static Result notCovered() {
            return new Result(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    /**
     * @param earnedGross      gross wages paid for the month, after loss of pay
     * @param periodStartGross the employee's full monthly gross when the contribution period began (or
     *                         when they joined, if that was later) — what decides coverage
     * @param paidDays         days paid in the month, for the daily-wage exemption; zero skips the test
     */
    public static Result compute(BigDecimal earnedGross, BigDecimal periodStartGross, double paidDays,
                                 BigDecimal employeeRate, BigDecimal employerRate, BigDecimal wageCeiling) {
        if (earnedGross == null || earnedGross.signum() <= 0 || periodStartGross == null) {
            return Result.notCovered();
        }
        if (periodStartGross.compareTo(wageCeiling) > 0) {
            return Result.notCovered();
        }
        BigDecimal employer = pct(earnedGross, employerRate);
        BigDecimal employee = pct(earnedGross, employeeRate);
        if (paidDays > 0) {
            BigDecimal daily = earnedGross.divide(BigDecimal.valueOf(paidDays), 2, RoundingMode.HALF_UP);
            if (daily.compareTo(LOW_WAGE_DAILY_LIMIT) <= 0) {
                employee = BigDecimal.ZERO;
            }
        }
        return new Result(true, earnedGross.setScale(2, RoundingMode.HALF_UP), employee, employer);
    }

    /** First day of the contribution period containing {@code month}: 1 April or 1 October. */
    public static LocalDate periodStart(YearMonth month) {
        int m = month.getMonthValue();
        if (m >= 4 && m <= 9) {
            return LocalDate.of(month.getYear(), 4, 1);
        }
        return m >= 10 ? LocalDate.of(month.getYear(), 10, 1) : LocalDate.of(month.getYear() - 1, 10, 1);
    }

    /** A percentage, rounded UP to the whole rupee — the ESIC rule. */
    private static BigDecimal pct(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING);
    }
}
