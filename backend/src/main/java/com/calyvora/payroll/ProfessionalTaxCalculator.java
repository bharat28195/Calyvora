package com.calyvora.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Professional tax for one employee for one month.
 *
 * <p>Pure, like the other statutory calculators. Professional tax is a state levy, so the answer turns
 * on where the employee works (their PT state on the finance record), and states disagree about
 * almost everything — the slabs, the basis, even how often it is collected:
 *
 * <ul>
 *   <li><b>Monthly states</b> (Maharashtra, Karnataka, West Bengal, Telangana, Andhra Pradesh,
 *       Gujarat, Assam, Mizoram, Nagaland, Sikkim, Punjab) slab the salary paid in the month. Two of
 *       them collect a larger amount in February so the year totals the constitutional cap of 2,500.</li>
 *   <li><b>Annual states</b> (Madhya Pradesh, Jharkhand, Bihar, Manipur) slab the year's income. It is
 *       deducted in twelve instalments with the rounding remainder in March, the way Madhya Pradesh's
 *       own schedule prints it (208 × 11 + 212).</li>
 *   <li><b>Half-yearly states</b> (Tamil Nadu, Kerala, Puducherry) slab the half-year's income and
 *       collect twice, in September and March.</li>
 * </ul>
 *
 * <p>Slabs as published for FY 2026-27 (checked against state schedules in October 2026). Tamil Nadu
 * uses the Greater Chennai Corporation slabs; other local bodies may differ within the same cap.
 * Odisha repealed the tax from 1 April 2026. Meghalaya and Tripura levy it but their current schedules
 * are notified rather than in the Act, so they are reported as {@code supported = false} and a company
 * there adds PT as a template deduction until the schedule is added here.
 *
 * <p>Anything not listed — Delhi, Haryana, Uttar Pradesh, Rajasthan, Goa and the rest — levies no
 * professional tax, and the answer is zero.
 */
public final class ProfessionalTaxCalculator {

    private ProfessionalTaxCalculator() {
    }

    /**
     * @param amount    deducted this month
     * @param stateCode the two-letter code the state resolved to, or null when no state was given
     * @param supported false only for states that levy PT but whose schedule this class does not hold
     */
    public record Result(BigDecimal amount, String stateCode, boolean supported) {
    }

    /** {@code upTo} inclusive; null means "and above". */
    private record Slab(Long upTo, int amount) {
    }

    private enum Basis { MONTHLY, ANNUAL, HALF_YEARLY }

    /**
     * @param februaryAmount for monthly states that collect more in February to reach the annual cap;
     *                       applies to the top slab only. Zero when the state has no such rule.
     */
    private record Schedule(Basis basis, List<Slab> slabs, int februaryAmount) {
    }

    private static final Map<String, Schedule> SCHEDULES = Map.ofEntries(
            Map.entry("KA", new Schedule(Basis.MONTHLY, List.of(new Slab(24_999L, 0), new Slab(null, 200)), 300)),
            Map.entry("WB", new Schedule(Basis.MONTHLY, List.of(new Slab(10_000L, 0), new Slab(15_000L, 110),
                    new Slab(25_000L, 130), new Slab(40_000L, 150), new Slab(null, 200)), 0)),
            Map.entry("TG", new Schedule(Basis.MONTHLY, List.of(new Slab(15_000L, 0), new Slab(20_000L, 150),
                    new Slab(null, 200)), 0)),
            Map.entry("AP", new Schedule(Basis.MONTHLY, List.of(new Slab(15_000L, 0), new Slab(20_000L, 150),
                    new Slab(null, 200)), 0)),
            Map.entry("GJ", new Schedule(Basis.MONTHLY, List.of(new Slab(12_000L, 0), new Slab(null, 200)), 0)),
            Map.entry("AS", new Schedule(Basis.MONTHLY, List.of(new Slab(10_000L, 0), new Slab(15_000L, 150),
                    new Slab(25_000L, 180), new Slab(null, 208)), 0)),
            Map.entry("MZ", new Schedule(Basis.MONTHLY, List.of(new Slab(5_000L, 0), new Slab(8_000L, 75),
                    new Slab(10_000L, 120), new Slab(12_000L, 150), new Slab(15_000L, 180),
                    new Slab(20_000L, 195), new Slab(null, 208)), 0)),
            // Nagaland's schedule overlaps at the boundaries and says the higher rate applies, so each
            // boundary belongs to the slab above it.
            Map.entry("NL", new Schedule(Basis.MONTHLY, List.of(new Slab(3_999L, 0), new Slab(4_999L, 35),
                    new Slab(6_999L, 75), new Slab(8_999L, 110), new Slab(11_999L, 180), new Slab(null, 208)), 0)),
            Map.entry("SK", new Schedule(Basis.MONTHLY, List.of(new Slab(20_000L, 0), new Slab(30_000L, 125),
                    new Slab(40_000L, 150), new Slab(null, 200)), 0)),
            Map.entry("PB", new Schedule(Basis.MONTHLY, List.of(new Slab(null, 200)), 0)),

            Map.entry("MP", new Schedule(Basis.ANNUAL, List.of(new Slab(225_000L, 0), new Slab(300_000L, 1_500),
                    new Slab(400_000L, 2_000), new Slab(null, 2_500)), 0)),
            Map.entry("JH", new Schedule(Basis.ANNUAL, List.of(new Slab(300_000L, 0), new Slab(500_000L, 1_200),
                    new Slab(800_000L, 1_800), new Slab(1_000_000L, 2_100), new Slab(null, 2_500)), 0)),
            Map.entry("BR", new Schedule(Basis.ANNUAL, List.of(new Slab(300_000L, 0), new Slab(500_000L, 1_000),
                    new Slab(1_000_000L, 2_000), new Slab(null, 2_500)), 0)),
            Map.entry("MN", new Schedule(Basis.ANNUAL, List.of(new Slab(50_000L, 0), new Slab(75_000L, 1_200),
                    new Slab(100_000L, 2_000), new Slab(125_000L, 2_400), new Slab(null, 2_500)), 0)),

            Map.entry("TN", new Schedule(Basis.HALF_YEARLY, List.of(new Slab(21_000L, 0), new Slab(30_000L, 180),
                    new Slab(45_000L, 425), new Slab(60_000L, 930), new Slab(75_000L, 1_025), new Slab(null, 1_250)), 0)),
            Map.entry("KL", new Schedule(Basis.HALF_YEARLY, List.of(new Slab(11_999L, 0), new Slab(17_999L, 320),
                    new Slab(29_999L, 450), new Slab(44_999L, 600), new Slab(99_999L, 750),
                    new Slab(124_999L, 1_000), new Slab(null, 1_250)), 0)),
            Map.entry("PY", new Schedule(Basis.HALF_YEARLY, List.of(new Slab(99_999L, 0), new Slab(200_000L, 250),
                    new Slab(300_000L, 500), new Slab(400_000L, 750), new Slab(500_000L, 1_000),
                    new Slab(null, 1_250)), 0))
    );

    /** States that levy PT whose schedule is not held here — reported, never silently zero. */
    private static final List<String> UNSUPPORTED = List.of("ML", "TR");

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("MAHARASHTRA", "MH"), Map.entry("KARNATAKA", "KA"), Map.entry("WEST BENGAL", "WB"),
            Map.entry("TELANGANA", "TG"), Map.entry("ANDHRA PRADESH", "AP"), Map.entry("GUJARAT", "GJ"),
            Map.entry("ASSAM", "AS"), Map.entry("MIZORAM", "MZ"), Map.entry("NAGALAND", "NL"),
            Map.entry("SIKKIM", "SK"), Map.entry("PUNJAB", "PB"), Map.entry("MADHYA PRADESH", "MP"),
            Map.entry("JHARKHAND", "JH"), Map.entry("BIHAR", "BR"), Map.entry("MANIPUR", "MN"),
            Map.entry("TAMIL NADU", "TN"), Map.entry("KERALA", "KL"), Map.entry("PUDUCHERRY", "PY"),
            Map.entry("PONDICHERRY", "PY"), Map.entry("MEGHALAYA", "ML"), Map.entry("TRIPURA", "TR"),
            Map.entry("ODISHA", "OD"), Map.entry("ORISSA", "OD"), Map.entry("TS", "TG"), Map.entry("TN", "TN"),
            Map.entry("DELHI", "DL"), Map.entry("HARYANA", "HR"), Map.entry("UTTAR PRADESH", "UP"),
            Map.entry("RAJASTHAN", "RJ"), Map.entry("GOA", "GA"), Map.entry("CHHATTISGARH", "CG"),
            Map.entry("UTTARAKHAND", "UK"), Map.entry("HIMACHAL PRADESH", "HP"), Map.entry("CHANDIGARH", "CH"));

    /** "Karnataka", "karnataka", "KA" and " Tamil  Nadu " all resolve; anything unrecognised is null. */
    public static String stateCode(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String s = state.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (NAMES.containsKey(s)) {
            return NAMES.get(s);
        }
        return s.length() == 2 ? s : null;
    }

    /**
     * @param earnedGross     the salary paid this month, after loss of pay — the basis in monthly states
     * @param contractedGross the full monthly gross — projected over the year or half-year in the
     *                        annual and half-yearly states, which slab income for the period
     * @param gender          MALE / FEMALE / OTHER / null; only Maharashtra reads it
     * @param dateOfBirth     only Karnataka reads it (60 and over are exempt); null is not exempt
     */
    public static Result compute(String state, String gender, LocalDate dateOfBirth,
                                 BigDecimal earnedGross, BigDecimal contractedGross, YearMonth month) {
        String code = stateCode(state);
        if (code == null) {
            return new Result(BigDecimal.ZERO, null, true);
        }
        if (UNSUPPORTED.contains(code)) {
            return new Result(BigDecimal.ZERO, code, false);
        }
        if (earnedGross == null || earnedGross.signum() <= 0) {
            return new Result(BigDecimal.ZERO, code, true);
        }
        boolean february = month.getMonth() == Month.FEBRUARY;

        if ("MH".equals(code)) {
            return new Result(BigDecimal.valueOf(maharashtra(earnedGross, gender, february)), code, true);
        }
        if ("KA".equals(code) && dateOfBirth != null
                && !dateOfBirth.plusYears(60).isAfter(month.atEndOfMonth())) {
            return new Result(BigDecimal.ZERO, code, true);
        }
        Schedule schedule = SCHEDULES.get(code);
        if (schedule == null) {
            return new Result(BigDecimal.ZERO, code, true);   // a state with no professional tax
        }
        // Jharkhand exempts anyone whose monthly salary does not exceed 15,000, whatever the annual slab.
        if ("JH".equals(code) && contractedGross.compareTo(BigDecimal.valueOf(15_000)) <= 0) {
            return new Result(BigDecimal.ZERO, code, true);
        }

        return switch (schedule.basis()) {
            case MONTHLY -> {
                int amount = slab(schedule.slabs(), earnedGross.longValue());
                boolean topSlab = amount == schedule.slabs().get(schedule.slabs().size() - 1).amount();
                if (february && topSlab && schedule.februaryAmount() > 0) {
                    amount = schedule.februaryAmount();
                }
                yield new Result(BigDecimal.valueOf(amount), code, true);
            }
            case ANNUAL -> {
                int annual = slab(schedule.slabs(), contractedGross.multiply(BigDecimal.valueOf(12)).longValue());
                int instalment = annual / 12;
                int amount = month.getMonth() == Month.MARCH ? annual - instalment * 11 : instalment;
                yield new Result(BigDecimal.valueOf(amount), code, true);
            }
            case HALF_YEARLY -> {
                boolean collectionMonth = month.getMonth() == Month.SEPTEMBER || month.getMonth() == Month.MARCH;
                int amount = collectionMonth
                        ? slab(schedule.slabs(), contractedGross.multiply(BigDecimal.valueOf(6)).longValue())
                        : 0;
                yield new Result(BigDecimal.valueOf(amount), code, true);
            }
        };
    }

    /** Men from 7,501; women from 25,001 (raised in 2023). 300 in February in the top slab. */
    private static int maharashtra(BigDecimal earned, String gender, boolean february) {
        long pay = earned.longValue();
        if ("FEMALE".equals(gender)) {
            return pay <= 25_000 ? 0 : (february ? 300 : 200);
        }
        if (pay <= 7_500) return 0;
        if (pay <= 10_000) return 175;
        return february ? 300 : 200;
    }

    private static int slab(List<Slab> slabs, long income) {
        for (Slab s : slabs) {
            if (s.upTo() == null || income <= s.upTo()) {
                return s.amount();
            }
        }
        return 0;
    }
}
