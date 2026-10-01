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
public record MeResponse(UserView user, CompanyView company, String timezone) {

    public record UserView(String id, String email, String firstName, String lastName,
                           String role, String status) {
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
    public static MeResponse of(User user, Company company, CompanySettings settings, Employee employee) {
        String currency = settings == null ? "INR" : settings.getCurrency();
        String companyZone = Timezones.forCompany(settings).getId();
        String effectiveZone = Timezones.resolve(employee, settings).getId();
        return new MeResponse(
                new UserView(user.getId().toString(), user.getEmail(), user.getFirstName(),
                        user.getLastName(), user.getRole().name(), user.getStatus().name()),
                new CompanyView(company.getId().toString(), company.getName(), company.getSlug(),
                        company.getStatus().name(), currency, companyZone,
                        settings == null ? null : settings.getSessionIdleMinutes()),
                effectiveZone);
    }
}
