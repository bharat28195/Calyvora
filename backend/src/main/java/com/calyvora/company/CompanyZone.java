package com.calyvora.company;

import com.calyvora.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The clock a tenant actually lives on (Settings → Localization).
 *
 * <p>The server's own clock is the wrong clock wherever a calendar date is the unit of meaning:
 * on a UTC host an Indian employee clocking in at 04:15 IST lands at 22:45 the <em>previous</em>
 * day — wrong date, wrong payslip, and wrong side of a review period. Attendance learned this the
 * hard way (PD-16); this class exists so every other feature that turns a date into an instant
 * inherits the fix instead of rediscovering it.
 */
@Service
public class CompanyZone {

    private final CompanySettingsRepository companySettingsRepository;

    public CompanyZone(CompanySettingsRepository companySettingsRepository) {
        this.companySettingsRepository = companySettingsRepository;
    }

    /** The current tenant's zone; UTC when unset or unparseable, never an exception. */
    @Transactional(readOnly = true)
    public ZoneId forCurrentTenant() {
        return companySettingsRepository.findById(TenantContext.getCompanyId())
                .map(CompanySettings::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .map(CompanyZone::parseOrUtc)
                .orElse(ZoneOffset.UTC);
    }

    /** The first instant of {@code date} in the tenant's zone. */
    public java.time.Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(forCurrentTenant()).toInstant();
    }

    /**
     * The first instant <em>after</em> {@code date} in the tenant's zone — the exclusive upper bound
     * for "everything that happened on or before this day".
     */
    public java.time.Instant endOfDayExclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay(forCurrentTenant()).toInstant();
    }

    private static ZoneId parseOrUtc(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (RuntimeException badZone) {
            return ZoneOffset.UTC;   // a bad setting must not break the request
        }
    }
}
