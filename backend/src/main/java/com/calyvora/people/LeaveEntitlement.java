package com.calyvora.people;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/**
 * How many days somebody has earned, and how many survived from last year.
 *
 * <p>Deliberately a pure function of (policy, join date, today, days used per year) with no
 * repository, no entity and no clock of its own. Accrual is the part of leave that people argue
 * about, so it has to be testable by writing down a date and an answer — every awkward case here
 * (joining mid-month, a carry-forward cap, a year with no usage) is a unit test rather than an
 * integration test that takes twenty-five seconds to tell you a rounding rule is wrong.
 *
 * <p>Everything is {@link BigDecimal} because half-days are normal in leave policy and doubles get
 * this wrong in ways nobody notices until a balance reads 12.499999.
 */
final class LeaveEntitlement {

    /**
     * Zero, at the same scale as every other answer.
     *
     * <p>{@code BigDecimal.ZERO} is scale 0 and {@code 0.0} is scale 1, and they are not
     * {@code equals} — so a bare ZERO on an early return makes a caller's comparison depend on which
     * branch produced the number. Everything that leaves this class goes through the same scale.
     */
    private static final BigDecimal ZERO_DAYS = BigDecimal.ZERO.setScale(1);

    private LeaveEntitlement() {
    }

    /** What an employee has available in {@code year}, and where it came from. */
    record Accrued(BigDecimal earnedThisYear, BigDecimal carriedIn) {

        BigDecimal total() {
            return earnedThisYear.add(carriedIn);
        }
    }

    /**
     * Days earned in {@code year} by somebody who joined on {@code joinedOn}, as at {@code asOf}.
     *
     * <p>Rules, stated because they are choices and not laws:
     * <ul>
     *   <li>Nothing is earned before joining, and nothing for a year that has not started.</li>
     *   <li>ANNUAL grants the whole entitlement at the start of the year — but pro-rates by the
     *       months remaining when somebody joins mid-year, because granting a full year's holiday to
     *       a December joiner is the kind of generosity a company should opt into, not inherit.</li>
     *   <li>MONTHLY grants one twelfth per <em>whole calendar month</em> worked. A joiner on the 20th
     *       earns nothing for that month; their first is the next one, credited when it ends.</li>
     * </ul>
     */
    static BigDecimal earnedIn(LeavePolicy policy, LocalDate joinedOn, int year, LocalDate asOf) {
        BigDecimal perYear = policy.getDaysPerYear();
        if (perYear.signum() <= 0 || asOf.getYear() < year) {
            return ZERO_DAYS;
        }
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate joined = joinedOn == null ? yearStart : joinedOn;
        if (joined.isAfter(yearEnd)) {
            return ZERO_DAYS;   // joined after this year ended
        }
        LocalDate start = joined.isAfter(yearStart) ? joined : yearStart;

        if (policy.getAccrual() == LeaveAccrual.ANNUAL) {
            int monthsRemaining = 12 - (start.getMonthValue() - 1);
            return monthsRemaining >= 12 ? scale(perYear) : share(perYear, monthsRemaining);
        }
        return share(perYear, wholeCalendarMonthsWorked(year, start, asOf));
    }

    /**
     * Calendar months of {@code year} the employee worked in full, as at {@code asOf}.
     *
     * <p>Calendar months rather than anniversaries — a month is earned when the month itself ends,
     * not on the monthly anniversary of the join date. Both are defensible, and this is the one
     * Indian payroll actually uses: leave is credited with the month, so everybody in the company
     * gains a day on the same date and can predict it.
     *
     * <p>The first attempt here counted anniversaries with {@code ChronoUnit.MONTHS} and produced
     * <strong>11 for a full calendar year</strong>: January to 31 December is eleven complete
     * anniversary months, because the twelfth lands on 1 January. A rule that cannot award a whole
     * year to somebody who worked one is the wrong rule, and the test that caught it is
     * {@code a_year_that_has_finished_accrues_in_full_not_up_to_today}.
     *
     * <p>A partial joining month earns nothing. Someone starting on 20 August earns from September.
     * This is deliberately the simple, predictable reading; pro-rating the joining month by days is a
     * policy choice a company should opt into rather than discover.
     */
    private static int wholeCalendarMonthsWorked(int year, LocalDate start, LocalDate asOf) {
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        // A year already finished accrues up to its end, not up to today.
        LocalDate cutoff = asOf.isBefore(yearEnd) ? asOf : yearEnd;
        int months = 0;
        for (int m = 1; m <= 12; m++) {
            LocalDate first = LocalDate.of(year, m, 1);
            LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
            boolean employedAllMonth = !start.isAfter(first);
            boolean monthIsOver = !cutoff.isBefore(last);
            if (employedAllMonth && monthIsOver) {
                months++;
            }
        }
        return months;
    }

    /** {@code months} twelfths of a year's entitlement. */
    private static BigDecimal share(BigDecimal perYear, int months) {
        if (months <= 0) {
            return ZERO_DAYS;
        }
        return scale(perYear.multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP));
    }

    /**
     * Days carried into {@code year}, walking forward from the year of joining.
     *
     * <p>Forward from joining rather than recursing backwards from today: carry-forward compounds —
     * what survives 2025 depends on what survived 2024 — and a backwards recursion has no natural
     * base case, so it either runs to the epoch or stops at an arbitrary depth and quietly reports a
     * number that is too small. Walking forward from the first year the person existed terminates on
     * a fact rather than on a guess.
     *
     * @param usedByYear days taken in each year, keyed by year
     */
    static BigDecimal carriedInto(LeavePolicy policy, LocalDate joinedOn, int year,
                                  LocalDate asOf, Map<Integer, BigDecimal> usedByYear) {
        BigDecimal cap = policy.getCarryForwardCap();
        if (cap.signum() <= 0) {
            return ZERO_DAYS;   // use it or lose it
        }
        int firstYear = joinedOn != null ? joinedOn.getYear() : year;
        if (firstYear >= year) {
            return ZERO_DAYS;   // no previous year to carry from
        }

        BigDecimal carry = BigDecimal.ZERO;
        for (int y = firstYear; y < year; y++) {
            BigDecimal earned = earnedIn(policy, joinedOn, y, asOf);
            BigDecimal used = usedByYear.getOrDefault(y, BigDecimal.ZERO);
            BigDecimal leftOver = earned.add(carry).subtract(used);
            carry = leftOver.signum() <= 0 ? ZERO_DAYS : leftOver.min(cap);
        }
        return scale(carry);
    }

    static Accrued accrued(LeavePolicy policy, LocalDate joinedOn, int year,
                           LocalDate asOf, Map<Integer, BigDecimal> usedByYear) {
        return new Accrued(earnedIn(policy, joinedOn, year, asOf),
                carriedInto(policy, joinedOn, year, asOf, usedByYear));
    }

    /** Half-day precision, which is as fine as any leave policy in practice goes. */
    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}
