package com.calyvora.tax;

import java.time.LocalDate;

/**
 * Which age rules apply to a resident individual for one tax year.
 *
 * <p>Age is decided "at any time during the year": someone who turns 60 in March is a senior citizen
 * for the whole year. The law also treats a person as attaining an age on the day <em>before</em>
 * their birthday, so somebody born on 1 April 1967 is 60 on 31 March 2027 and a senior for tax year
 * 2026-27. Getting this wrong by a day moves a whole year's exemption.
 */
public enum AgeBand {
    BELOW_60,
    SENIOR,        // 60 to 79
    SUPER_SENIOR;  // 80 and above

    /** The band for a date of birth in a tax year; unknown dates are treated as below 60. */
    public static AgeBand of(LocalDate dateOfBirth, FinancialYear year) {
        if (dateOfBirth == null) {
            return BELOW_60;
        }
        LocalDate lastDay = year.end();
        // Attains N on the day before the Nth birthday: born on or before (lastDay + 1 day − N years).
        if (!dateOfBirth.isAfter(lastDay.plusDays(1).minusYears(80))) {
            return SUPER_SENIOR;
        }
        if (!dateOfBirth.isAfter(lastDay.plusDays(1).minusYears(60))) {
            return SENIOR;
        }
        return BELOW_60;
    }

    public boolean isSenior() {
        return this != BELOW_60;
    }
}
