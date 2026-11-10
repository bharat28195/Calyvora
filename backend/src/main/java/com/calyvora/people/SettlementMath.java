package com.calyvora.people;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The arithmetic of a full and final settlement (PD-73), kept apart from the data it reads so each
 * rule can be checked on its own.
 *
 * <p>Gratuity follows the Payment of Gratuity Act, 1972 for an establishment covered by it: 15 days'
 * wages for every completed year, a day's wage being a month's basic ÷ 26; a final part-year of six
 * months or more counts as a year; due after five years of continuous service, which the courts read
 * as met at four years and 240 days; capped at ₹20,00,000.
 */
final class SettlementMath {

    static final BigDecimal GRATUITY_CAP = new BigDecimal("2000000");

    private SettlementMath() {
    }

    record Service(int years, int months, int days, boolean gratuityDue, int yearsCounted) {
        String describe() {
            StringBuilder s = new StringBuilder();
            if (years > 0) s.append(years).append(years == 1 ? " year" : " years");
            if (months > 0) s.append(s.length() > 0 ? ", " : "").append(months).append(months == 1 ? " month" : " months");
            if (days > 0 || s.length() == 0) s.append(s.length() > 0 ? ", " : "").append(days).append(days == 1 ? " day" : " days");
            return s.toString();
        }
    }

    /** Length of service, counting the last working day. */
    static Service service(LocalDate start, LocalDate lastDay) {
        if (start == null || lastDay == null || lastDay.isBefore(start)) return new Service(0, 0, 0, false, 0);
        Period p = Period.between(start, lastDay.plusDays(1));
        boolean due = p.getYears() >= 5
                || (p.getYears() == 4 && ChronoUnit.DAYS.between(start.plusYears(4), lastDay.plusDays(1)) >= 240);
        int counted = p.getYears() + (p.getMonths() >= 6 ? 1 : 0);
        return new Service(p.getYears(), p.getMonths(), p.getDays(), due, due ? Math.max(counted, 5) : 0);
    }

    /** 15/26 × monthly basic × years counted, capped. Zero when not due. */
    static BigDecimal gratuity(Service service, BigDecimal monthlyBasic) {
        if (!service.gratuityDue() || monthlyBasic == null) return BigDecimal.ZERO;
        BigDecimal amount = monthlyBasic.multiply(BigDecimal.valueOf(15L * service.yearsCounted()))
                .divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
        return amount.min(GRATUITY_CAP);
    }

    /** A day's pay for encashment and notice: BASIC_26 (the norm), BASIC_30 or GROSS_30. */
    static BigDecimal perDay(String basis, BigDecimal monthlyBasic, BigDecimal monthlyGross) {
        return switch (basis == null ? "BASIC_26" : basis) {
            case "BASIC_30" -> monthlyBasic.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
            case "GROSS_30" -> monthlyGross.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
            default -> monthlyBasic.divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
        };
    }

    private static final Pattern NOTICE = Pattern.compile(
            "(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|twelve|fifteen|thirty|sixty|ninety)\\s*(day|week|month)",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> WORDS = Map.ofEntries(Map.entry("one", 1), Map.entry("two", 2),
            Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7),
            Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("twelve", 12),
            Map.entry("fifteen", 15), Map.entry("thirty", 30), Map.entry("sixty", 60), Map.entry("ninety", 90));

    /** "60 days", "two months", "1 month's notice" → days; null when there is nothing to read. */
    static Integer noticeDays(String text) {
        if (text == null) return null;
        Matcher m = NOTICE.matcher(text);
        if (!m.find()) return null;
        String n = m.group(1).toLowerCase(Locale.ROOT);
        int count = Character.isDigit(n.charAt(0)) ? Integer.parseInt(n) : WORDS.get(n);
        return switch (m.group(2).toLowerCase(Locale.ROOT)) {
            case "week" -> count * 7;
            case "month" -> count * 30;
            default -> count;
        };
    }

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
