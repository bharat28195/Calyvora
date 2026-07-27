package com.calyvora.auth.dto;

import com.calyvora.company.Company;
import com.calyvora.company.CompanySettings;
import com.calyvora.identity.User;

/** Current user + company. Matches the frontend {@code Me} type. */
public record MeResponse(UserView user, CompanyView company) {

    public record UserView(String id, String email, String firstName, String lastName,
                           String role, String status) {
    }

    public record CompanyView(String id, String name, String slug, String status,
                              String currency, String timezone) {
    }

    /** Currency/timezone come from settings so the whole app can localize from {@code /me}. */
    public static MeResponse of(User user, Company company, CompanySettings settings) {
        String currency = settings == null ? "INR" : settings.getCurrency();
        String timezone = settings == null ? "UTC" : settings.getTimezone();
        return new MeResponse(
                new UserView(user.getId().toString(), user.getEmail(), user.getFirstName(),
                        user.getLastName(), user.getRole().name(), user.getStatus().name()),
                new CompanyView(company.getId().toString(), company.getName(), company.getSlug(),
                        company.getStatus().name(), currency, timezone));
    }
}
