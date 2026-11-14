package com.calyvora.company.dto;

import com.calyvora.company.CompanySettings;

public record CompanySettingsResponse(String companyId, String timezone, String locale,
                                      String currency, String legalName, String address, String logoUrl,
                                      Integer sessionIdleMinutes,
                                      /** Whether attendance is tracked in Orbit; the app hides check-in when it is not. */
                                      boolean trackAttendance) {

    public static CompanySettingsResponse of(CompanySettings settings) {
        return new CompanySettingsResponse(settings.getCompanyId().toString(),
                settings.getTimezone(), settings.getLocale(), settings.getCurrency(),
                settings.getLegalName(), settings.getAddress(), settings.getLogoUrl(),
                settings.getSessionIdleMinutes(), settings.getAttendanceRulesFrom() != null);
    }
}
