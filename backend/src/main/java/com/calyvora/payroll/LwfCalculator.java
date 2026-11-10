package com.calyvora.payroll;

import java.math.BigDecimal;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Labour Welfare Fund for one employee for one month.
 *
 * <p>A small state levy with fixed amounts rather than percentages, collected on a state's own
 * calendar: every month (Punjab, Chandigarh), every half-year in the June and December payrolls
 * (Maharashtra, Gujarat, West Bengal, Goa, Odisha, Delhi), or once a year in the December payroll
 * (Karnataka, Tamil Nadu, Andhra Pradesh, Telangana). The state is where the employee works — the
 * same field professional tax reads.
 *
 * <p>Amounts as published for 2026 (Maharashtra raised to 25/75 in March 2024; Karnataka 50/100 in
 * 2025). Delhi's monthly 0.75 / 2.25 is collected half-yearly, six months at a time.
 *
 * <p>Madhya Pradesh, Haryana, Chhattisgarh and Kerala levy LWF but published sources disagree on
 * the current amounts, so they are reported as {@code supported = false} — the same treatment as an
 * unheld professional-tax schedule — rather than deducted on a guess. A company there adds LWF as a
 * template deduction until the schedule is confirmed and added here.
 */
public final class LwfCalculator {

    private LwfCalculator() {
    }

    public record Result(BigDecimal employee, BigDecimal employer, String stateCode, boolean supported) {
        static Result none(String code, boolean supported) {
            return new Result(BigDecimal.ZERO, BigDecimal.ZERO, code, supported);
        }
    }

    private enum Frequency { MONTHLY, HALF_YEARLY, ANNUAL }

    private record Rate(Frequency frequency, BigDecimal employee, BigDecimal employer) {
        static Rate of(Frequency f, String employee, String employer) {
            return new Rate(f, new BigDecimal(employee), new BigDecimal(employer));
        }
    }

    private static final Map<String, Rate> RATES = Map.ofEntries(
            Map.entry("MH", Rate.of(Frequency.HALF_YEARLY, "25", "75")),
            Map.entry("GJ", Rate.of(Frequency.HALF_YEARLY, "6", "12")),
            Map.entry("WB", Rate.of(Frequency.HALF_YEARLY, "3", "30")),
            Map.entry("GA", Rate.of(Frequency.HALF_YEARLY, "60", "180")),
            Map.entry("OD", Rate.of(Frequency.HALF_YEARLY, "20", "40")),
            Map.entry("DL", Rate.of(Frequency.HALF_YEARLY, "4.50", "13.50")),   // 0.75 / 2.25 × 6 months
            Map.entry("KA", Rate.of(Frequency.ANNUAL, "50", "100")),
            Map.entry("TN", Rate.of(Frequency.ANNUAL, "20", "40")),
            Map.entry("AP", Rate.of(Frequency.ANNUAL, "30", "70")),
            Map.entry("TG", Rate.of(Frequency.ANNUAL, "2", "5")),
            Map.entry("PB", Rate.of(Frequency.MONTHLY, "5", "20")),
            Map.entry("CH", Rate.of(Frequency.MONTHLY, "5", "20")));

    /** Levy LWF, but the current amounts could not be confirmed. */
    private static final List<String> UNSUPPORTED = List.of("MP", "HR", "CG", "KL");

    /**
     * @param state       the employee's work state, as typed (resolved like professional tax)
     * @param earnedGross wages paid this month; nobody who was paid nothing contributes
     */
    public static Result compute(String state, BigDecimal earnedGross, YearMonth month) {
        String code = ProfessionalTaxCalculator.stateCode(state);
        if (code == null) {
            return Result.none(null, true);
        }
        if (UNSUPPORTED.contains(code)) {
            return Result.none(code, false);
        }
        Rate rate = RATES.get(code);
        if (rate == null || earnedGross == null || earnedGross.signum() <= 0) {
            return Result.none(code, true);
        }
        Month m = month.getMonth();
        boolean due = switch (rate.frequency()) {
            case MONTHLY -> true;
            case HALF_YEARLY -> m == Month.JUNE || m == Month.DECEMBER;
            case ANNUAL -> m == Month.DECEMBER;
        };
        return due ? new Result(rate.employee(), rate.employer(), code, true) : Result.none(code, true);
    }
}
