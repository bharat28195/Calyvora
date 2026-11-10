package com.calyvora.people;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * What one month's salary actually was, by date.
 *
 * <p>Payroll used to take the newest salary row for every month, so a raise effective in June
 * re-priced May, and income tax assumed today's salary had been paid all year. Both are wrong the
 * moment anybody gets a raise, joins mid-month or leaves.
 *
 * <p>The rule, the one Keka and Zoho apply: each day of the month is paid at the salary in force on
 * that day, and the month is the sum of its days. Two boundaries:
 * <ul>
 *   <li>Days before the employee's start date or after their end date are not paid at all.</li>
 *   <li>Days before the <em>first</em> salary row are paid at that first salary. The first row's date
 *       is when somebody typed it in, not when the person was hired — an HR manager entering an
 *       existing employee's salary today must not halve this month's pay. The start date is what
 *       says when pay begins.</li>
 * </ul>
 *
 * <p>A month with one salary throughout and no boundary inside it is exactly annual / 12, with no
 * per-day rounding — the common case stays the number people expect.
 */
public final class SalaryCalendar {

    private SalaryCalendar() {
    }

    /**
     * @param newestFirst every salary row for the employee, newest first (as the repositories return)
     * @return the month's gross; zero when the employee was not employed on any day of it; null when
     *         there is no salary on record at all
     */
    public static BigDecimal grossForMonth(List<CompensationRecord> newestFirst, YearMonth month,
                                           LocalDate startDate, LocalDate endDate) {
        if (newestFirst == null || newestFirst.isEmpty()) {
            return null;
        }
        LocalDate first = month.atDay(1), last = month.atEndOfMonth();
        LocalDate from = startDate != null && startDate.isAfter(first) ? startDate : first;
        LocalDate to = endDate != null && endDate.isBefore(last) ? endDate : last;
        if (from.isAfter(to)) {
            return BigDecimal.ZERO;
        }

        CompensationRecord atFrom = inForce(newestFirst, from);
        boolean wholeMonth = from.equals(first) && to.equals(last);
        if (wholeMonth && inForce(newestFirst, to) == atFrom && noChangeWithin(newestFirst, first, last)) {
            return monthly(atFrom);
        }

        int days = month.lengthOfMonth();
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            total = total.add(monthly(inForce(newestFirst, d)).divide(BigDecimal.valueOf(days), 10, RoundingMode.HALF_UP));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** The salary row in force on {@code date}: the newest that took effect on or before it, else the first. */
    static CompensationRecord inForce(List<CompensationRecord> newestFirst, LocalDate date) {
        for (CompensationRecord r : newestFirst) {
            if (!r.getEffectiveDate().isAfter(date)) {
                return r;
            }
        }
        return newestFirst.get(newestFirst.size() - 1);
    }

    private static boolean noChangeWithin(List<CompensationRecord> newestFirst, LocalDate first, LocalDate last) {
        for (CompensationRecord r : newestFirst) {
            LocalDate e = r.getEffectiveDate();
            if (e.isAfter(first) && !e.isAfter(last)) {
                // A change that takes effect inside the month — unless it is the very first row, which
                // applies backwards anyway.
                if (r != newestFirst.get(newestFirst.size() - 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BigDecimal monthly(CompensationRecord r) {
        return r.getAnnualAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
}
