package com.calyvora.people;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Spells an amount out in words for the payslip footer ("One Lakh Eighty Thousand Six Hundred and
 * Sixty Five Rupees Only") — a payslip is conventionally expected to state the net in words as well
 * as figures, and it's the line a reader uses to catch a misplaced digit.
 *
 * <p>Uses the Indian numbering system (lakh / crore), which is what the currencies this is used with
 * expect.
 */
final class AmountInWords {

    private static final String[] UNITS = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
            "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private AmountInWords() {}

    /**
     * @param currency ISO code — decides the unit names ("Rupees"/"Paise" vs a generic fallback).
     */
    static String of(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP).abs();
        long whole = rounded.longValue();
        int fraction = rounded.subtract(BigDecimal.valueOf(whole)).movePointRight(2).intValue();

        String major = majorUnit(currency);
        StringBuilder sb = new StringBuilder();
        if (amount.signum() < 0) {
            sb.append("Minus ");
        }
        sb.append(indian(whole)).append(' ').append(major);
        if (fraction > 0) {
            sb.append(" and ").append(indian(fraction)).append(' ').append(minorUnit(currency));
        }
        return sb.append(" Only").toString();
    }

    private static String majorUnit(String currency) {
        return "INR".equalsIgnoreCase(currency) ? "Rupees" : currency;
    }

    private static String minorUnit(String currency) {
        return "INR".equalsIgnoreCase(currency) ? "Paise" : "Cents";
    }

    /** Indian grouping: crore, lakh, thousand, hundred. */
    private static String indian(long n) {
        if (n == 0) {
            return "Zero";
        }
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000;
        n %= 10_000_000;
        long lakh = n / 100_000;
        n %= 100_000;
        long thousand = n / 1_000;
        n %= 1_000;
        long hundred = n / 100;
        long rest = n % 100;

        if (crore > 0) sb.append(indian(crore)).append(" Crore ");
        if (lakh > 0) sb.append(twoDigits(lakh)).append(" Lakh ");
        if (thousand > 0) sb.append(twoDigits(thousand)).append(" Thousand ");
        if (hundred > 0) sb.append(twoDigits(hundred)).append(" Hundred ");
        if (rest > 0) {
            if (sb.length() > 0) sb.append("and ");
            sb.append(twoDigits(rest)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String twoDigits(long n) {
        if (n < 20) {
            return UNITS[(int) n];
        }
        String tens = TENS[(int) (n / 10)];
        long unit = n % 10;
        return unit == 0 ? tens : tens + " " + UNITS[(int) unit];
    }
}
