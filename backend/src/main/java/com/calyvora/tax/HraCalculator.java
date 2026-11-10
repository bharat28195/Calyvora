package com.calyvora.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * House Rent Allowance exempt from tax (Schedule III of the 2025 Act; section 10(13A) before).
 *
 * <p>The exemption is worked out <b>month by month</b>, because every one of its three inputs can
 * change in the middle of a year — a raise moves the basic, a move moves the rent and the city. For
 * each month it is the least of:
 * <ol>
 *   <li>the HRA actually received that month;</li>
 *   <li>the rent paid that month, less 10% of that month's salary;</li>
 *   <li>50% of that month's salary in a metro city, 40% anywhere else.</li>
 * </ol>
 * "Salary" here is basic plus dearness allowance; Orbit's salary structures carry no DA, so it is
 * the basic. A month with no rent, or no HRA, exempts nothing.
 *
 * <p><b>Eight metros from tax year 2026-27.</b> The Income-tax Rules, 2026 added Bengaluru,
 * Hyderabad, Pune and Ahmedabad to the 50% list alongside Delhi, Mumbai, Kolkata and Chennai.
 */
public final class HraCalculator {

    private HraCalculator() {
    }

    /** The cities that earn 50% (any common spelling). Everything else is 40%. */
    private static final Set<String> METROS = Set.of(
            "delhi", "new delhi", "mumbai", "bombay", "kolkata", "calcutta", "chennai", "madras",
            "bengaluru", "bangalore", "hyderabad", "pune", "ahmedabad");

    public static boolean isMetro(String city) {
        return city != null && METROS.contains(city.trim().toLowerCase(Locale.ROOT));
    }

    /** One month's facts. */
    public record Month(String month, BigDecimal basic, BigDecimal hraReceived, BigDecimal rentPaid, boolean metro) {
    }

    /** One month's working, kept so the screen can show it. */
    public record MonthResult(String month, BigDecimal hraReceived, BigDecimal rentLessTenPercent,
                              BigDecimal percentOfBasic, BigDecimal exempt) {
    }

    public record Result(List<MonthResult> months, BigDecimal received, BigDecimal exempt) {
        /** The part of the HRA that stays taxable. */
        public BigDecimal taxable() {
            return received.subtract(exempt);
        }
    }

    public static Result compute(List<Month> months) {
        List<MonthResult> out = new ArrayList<>();
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal exempt = BigDecimal.ZERO;
        for (Month m : months) {
            BigDecimal hra = nz(m.hraReceived());
            BigDecimal basic = nz(m.basic());
            BigDecimal rent = nz(m.rentPaid());
            received = received.add(hra);
            BigDecimal rentLess = rent.subtract(basic.multiply(new BigDecimal("0.10"))).max(BigDecimal.ZERO);
            BigDecimal pct = basic.multiply(m.metro() ? new BigDecimal("0.50") : new BigDecimal("0.40"));
            BigDecimal e = rent.signum() <= 0 ? BigDecimal.ZERO : hra.min(rentLess).min(pct).max(BigDecimal.ZERO);
            e = e.setScale(2, RoundingMode.HALF_UP);
            exempt = exempt.add(e);
            out.add(new MonthResult(m.month(), hra, rentLess.setScale(2, RoundingMode.HALF_UP),
                    pct.setScale(2, RoundingMode.HALF_UP), e));
        }
        return new Result(List.copyOf(out), received.setScale(2, RoundingMode.HALF_UP),
                exempt.setScale(0, RoundingMode.DOWN));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null || v.signum() < 0 ? BigDecimal.ZERO : v;
    }
}
