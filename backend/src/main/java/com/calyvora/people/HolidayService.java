package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.people.dto.HolidayPayload;
import com.calyvora.people.dto.HolidayResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;

/**
 * The company holiday calendar. Everyone can see it (you need to know when the office is shut);
 * only Owner/Admin can change it — the controller enforces that.
 */
@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;

    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    /** The whole calendar, or one year when {@code year} is given. */
    @Transactional(readOnly = true)
    public List<HolidayResponse> list(Integer year) {
        UUID companyId = TenantContext.getCompanyId();
        List<Holiday> rows = year == null
                ? holidayRepository.findByCompanyIdOrderByDateAsc(companyId)
                : holidayRepository.findByCompanyIdAndDateBetweenOrderByDateAsc(companyId,
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        return rows.stream().map(HolidayResponse::of).toList();
    }

    /** The next few holidays from today — what the dashboard widget shows. */
    @Transactional(readOnly = true)
    public List<HolidayResponse> upcoming(int limit) {
        UUID companyId = TenantContext.getCompanyId();
        LocalDate today = LocalDate.now();
        return holidayRepository
                .findByCompanyIdAndDateBetweenOrderByDateAsc(companyId, today, today.plusYears(1)).stream()
                .limit(limit)
                .map(HolidayResponse::of)
                .toList();
    }

    @Transactional
    public HolidayResponse create(HolidayPayload p, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        if (p.name() == null || p.name().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Holiday name is required");
        }
        if (p.date() == null || p.date().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Holiday date is required");
        }
        Holiday h = new Holiday(UUID.randomUUID(), companyId, p.name().trim(), LocalDate.parse(p.date()),
                Boolean.TRUE.equals(p.optional()), blankToNull(p.note()), principal.userId());
        holidayRepository.save(h);
        return HolidayResponse.of(h);
    }

    @Transactional
    public HolidayResponse update(UUID id, HolidayPayload p) {
        Holiday h = require(id);
        if (p.name() != null && !p.name().isBlank()) h.setName(p.name().trim());
        if (p.date() != null && !p.date().isBlank()) h.setDate(LocalDate.parse(p.date()));
        if (p.optional() != null) h.setOptional(p.optional());
        if (p.note() != null) h.setNote(blankToNull(p.note()));
        return HolidayResponse.of(h);
    }

    @Transactional
    public void delete(UUID id) {
        holidayRepository.delete(require(id));
    }

    /**
     * Seeds a small default calendar for the current year so a new company (and the demo) doesn't
     * open onto an empty page. Dates are fixed-date holidays only — anything lunar or region-specific
     * is a judgement call the company should make, so we don't guess.
     */
    @Transactional
    public List<HolidayResponse> seedDefaults(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        if (holidayRepository.countByCompanyId(companyId) > 0) {
            return list(null);
        }
        int year = Year.now().getValue();
        record Default(String name, int month, int day) {}
        List<Default> defaults = List.of(
                new Default("New Year's Day", 1, 1),
                new Default("Republic Day", 1, 26),
                new Default("Labour Day", 5, 1),
                new Default("Independence Day", 8, 15),
                new Default("Gandhi Jayanti", 10, 2),
                new Default("Christmas Day", 12, 25));
        for (Default d : defaults) {
            holidayRepository.save(new Holiday(UUID.randomUUID(), companyId, d.name(),
                    LocalDate.of(year, d.month(), d.day()), false, null, principal.userId()));
        }
        return list(null);
    }

    private Holiday require(UUID id) {
        return holidayRepository.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Holiday not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
