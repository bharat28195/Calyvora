package com.calyvora.tax;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;

import java.time.LocalDate;
import java.time.Month;

/**
 * An Indian financial year — 1 April to 31 March — and the label everybody writes it with.
 *
 * <p>Worth its own type because the off-by-one is constant and invisible. In September 2026 the
 * financial year is 2026-27, but in February 2027 it is <em>still</em> 2026-27, and using the
 * calendar year would silently file three months of every employee's tax under the wrong year. The
 * rule is simply that the year begins in April, and putting it in one place means it is written once
 * rather than in every screen that needs to know.
 */
public record FinancialYear(int startYear) implements Comparable<FinancialYear> {

    public static FinancialYear of(LocalDate date) {
        // January to March belong to the year that began the previous April.
        int start = date.getMonthValue() >= Month.APRIL.getValue() ? date.getYear() : date.getYear() - 1;
        return new FinancialYear(start);
    }

    /** Parses "2026-27", and also "2026" for callers that only have the start. */
    public static FinancialYear parse(String label) {
        if (label == null || label.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A financial year is required, like 2026-27.");
        }
        String trimmed = label.trim();
        try {
            int dash = trimmed.indexOf('-');
            int start = Integer.parseInt(dash < 0 ? trimmed : trimmed.substring(0, dash));
            if (start < 2000 || start > 2100) {
                throw new NumberFormatException("out of range");
            }
            return new FinancialYear(start);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "'" + label + "' is not a financial year. Use the form 2026-27.");
        }
    }

    public LocalDate start() {
        return LocalDate.of(startYear, Month.APRIL, 1);
    }

    public LocalDate end() {
        return LocalDate.of(startYear + 1, Month.MARCH, 31);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start()) && !date.isAfter(end());
    }

    /**
     * How many months of this year have finished on a given date, 0 through 12.
     *
     * <p>Used to spread the year's tax over the pay runs that are left. A month is counted as gone
     * once it has ended, so on any day in September — the sixth month — five have finished.
     */
    public int monthsElapsed(LocalDate on) {
        if (on.isBefore(start())) {
            return 0;
        }
        if (on.isAfter(end())) {
            return 12;
        }
        return (int) java.time.temporal.ChronoUnit.MONTHS.between(
                java.time.YearMonth.from(start()), java.time.YearMonth.from(on));
    }

    /** "2026-27" — the two-digit second half is how it is written on every Indian form. */
    public String label() {
        return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
    }

    @Override
    public String toString() {
        return label();
    }

    @Override
    public int compareTo(FinancialYear other) {
        return Integer.compare(startYear, other.startYear);
    }
}
