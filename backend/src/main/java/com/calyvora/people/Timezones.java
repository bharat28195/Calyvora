package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.company.CompanySettings;

import java.time.ZoneId;

/**
 * Which clock a person's day runs on.
 *
 * <p>One chain, in one place: the employee's own zone if they have set one, else the company's, else
 * the product default. Three callers used to answer this separately and two of them fell back to UTC
 * — so a company that had never opened its settings page had every check-in stamped in UTC, and the
 * attendance screen said 03:45 for a nine o'clock arrival.
 *
 * <p>The default is Asia/Kolkata because that is what {@link CompanySettings} defaults to, and the
 * two must agree: a company with no settings row is a company that never changed anything, and it
 * should behave exactly like one that saved the defaults.
 */
public final class Timezones {

    private Timezones() {
    }

    /** What a company gets before it chooses. Matches the column default on company_settings. */
    public static final ZoneId DEFAULT = ZoneId.of("Asia/Kolkata");

    public static ZoneId resolve(Employee employee, CompanySettings settings) {
        ZoneId own = employee == null ? null : parse(employee.getTimezone());
        if (own != null) {
            return own;
        }
        return forCompany(settings);
    }

    public static ZoneId forCompany(CompanySettings settings) {
        ZoneId company = settings == null ? null : parse(settings.getTimezone());
        return company == null ? DEFAULT : company;
    }

    /**
     * A zone id the JDK recognises, or a 400 naming the problem.
     *
     * <p>Refused rather than stored and fallen back from: an employee who saves "IST" and is silently
     * treated as being in Kolkata has no way to learn that their setting did nothing.
     */
    public static String validOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (parse(trimmed) == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "'" + trimmed + "' is not a timezone. Use a name like Asia/Kolkata or Europe/Berlin.");
        }
        return trimmed;
    }

    private static ZoneId parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (RuntimeException badZone) {
            return null;
        }
    }
}
