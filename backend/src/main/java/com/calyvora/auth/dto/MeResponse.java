package com.calyvora.auth.dto;

import com.calyvora.company.Company;
import com.calyvora.company.CompanySettings;
import com.calyvora.identity.User;
import com.calyvora.people.Employee;
import com.calyvora.people.Timezones;

/**
 * Current user + company. Matches the frontend {@code Me} type.
 *
 * <p>{@code timezone} at the top level is the one the screens should set their clocks from: the
 * person's own if they have chosen one, else the company's. It is resolved by the same code that
 * stamps attendance punches, so what the clock on the attendance page shows and what the server
 * records when the button is pressed cannot disagree. {@code company.timezone} stays as well, for
 * the settings screen that edits it.
 */
public record MeResponse(UserView user, CompanyView company, String timezone,
                         /** The language to show the app in: the person's choice, else the company's, else English (V68). */
                         String language,
                         /** What this person may do, permission key → COMPANY or TEAM (PD-54). Drives the menu. */
                         java.util.Map<String, String> permissions) {

    /** @param mustChangePassword the password was chosen by someone else; the app asks for a new one first (V67) */
    public record UserView(String id, String email, String firstName, String lastName,
                           String role, String status, boolean mustChangePassword,
                           Preferences preferences) {
    }

    /**
     * What this person chose for themselves, exactly as saved — null where they left the default.
     * The account screen edits these; everything else reads the resolved values at the top level.
     */
    public record Preferences(String language, String timezone, String dateFormat, String timeFormat) {
    }

    public record CompanyView(String id, String name, String slug, String status,
                              String currency, String timezone, Integer sessionIdleMinutes) {
    }

    /**
     * Currency/timezone come from settings so the whole app can localize from {@code /me}.
     *
     * <p>A company with no settings row used to get "UTC" here, which is not any company's timezone
     * and was never what anybody chose — it was the value for "has not opened the settings page yet".
     * The fallback is now the same default the settings row itself would have had.
     */
    public static MeResponse of(User user, Company company, CompanySettings settings, Employee employee,
                                java.util.Map<String, String> permissions) {
        String currency = settings == null ? "INR" : settings.getCurrency();
        String companyZone = Timezones.forCompany(settings).getId();
        // The employee record holds the clock for anyone who has one (it is what stamps attendance);
        // somebody without one — the platform owner, an admin who is not on the payroll — keeps it on
        // their account.
        String effectiveZone = Timezones.resolve(employee, user.getTimezone(), settings).getId();
        String ownZone = employee != null ? employee.getTimezone() : user.getTimezone();
        return new MeResponse(
                new UserView(user.getId().toString(), user.getEmail(), user.getFirstName(),
                        user.getLastName(), user.getRole().name(), user.getStatus().name(),
                        user.isMustChangePassword(),
                        new Preferences(user.getLanguage(), ownZone, user.getDateFormat(), user.getTimeFormat())),
                new CompanyView(company.getId().toString(), company.getName(), company.getSlug(),
                        company.getStatus().name(), currency, companyZone,
                        settings == null ? null : settings.getSessionIdleMinutes()),
                effectiveZone,
                com.calyvora.auth.Preferences.effectiveLanguage(user.getLanguage(),
                        settings == null ? null : settings.getLocale()),
                permissions);
    }
}
