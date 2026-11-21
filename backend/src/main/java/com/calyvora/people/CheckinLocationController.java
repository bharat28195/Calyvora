package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Location at check-in (PD-81): the company's mode and offices, and where the day's check-ins happened.
 *
 * <p>Anyone signed in may read the mode and offices — the app needs them to know whether to ask for
 * location, and an office address is not a secret. Changing them, and seeing where people checked
 * in, is for whoever manages attendance company-wide.
 */
@RestController
@RequestMapping("/api/v1/people/attendance/location")
public class CheckinLocationController {

    public record Office(String id, String name, double latitude, double longitude, int radiusM) {
        static Office of(OfficeLocation o) {
            return new Office(o.getId().toString(), o.getName(), o.getLatitude(), o.getLongitude(), o.getRadiusM());
        }
    }

    public record Settings(String mode, List<Office> offices) {}

    public record ModeRequest(@NotBlank String mode) {}

    public record OfficeRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @Min(25) @Max(5000) Integer radiusM) {}

    /** One check-in/out session with where it happened. Names come from the day sheet. */
    public record Spot(String employeeId, String checkIn, String checkOut,
                       Double inLatitude, Double inLongitude, Integer inAccuracyM, String inOffice, Integer inDistanceM,
                       Double outLatitude, Double outLongitude, Integer outAccuracyM, String outOffice, Integer outDistanceM) {}

    private final CheckinLocationService service;
    private final OfficeLocationRepository offices;
    private final CompanySettingsRepository settings;
    private final AttendancePunchRepository punches;
    private final AttendanceService attendanceService;

    public CheckinLocationController(CheckinLocationService service, OfficeLocationRepository offices,
                                     CompanySettingsRepository settings, AttendancePunchRepository punches,
                                     AttendanceService attendanceService) {
        this.service = service;
        this.offices = offices;
        this.settings = settings;
        this.punches = punches;
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public Settings get() {
        UUID companyId = TenantContext.getCompanyId();
        return new Settings(service.mode(companyId).name(),
                offices.findByCompanyIdOrderByCreatedAtAsc(companyId).stream().map(Office::of).toList());
    }

    @PutMapping("/mode")
    @PreAuthorize("@perm.companyWide('ATTENDANCE_MANAGE')")
    @Transactional
    public Settings setMode(@Valid @RequestBody ModeRequest request) {
        CheckinLocationService.Mode mode;
        try {
            mode = CheckinLocationService.Mode.valueOf(request.mode().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Mode must be OFF, RECORD or ENFORCE");
        }
        UUID companyId = TenantContext.getCompanyId();
        CompanySettings s = settings.findById(companyId).orElseGet(() -> new CompanySettings(companyId));
        s.setCheckinLocationMode(mode.name());
        settings.save(s);
        return get();
    }

    @PostMapping("/offices")
    @PreAuthorize("@perm.companyWide('ATTENDANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Office addOffice(@Valid @RequestBody OfficeRequest r) {
        UUID companyId = TenantContext.getCompanyId();
        return Office.of(offices.save(new OfficeLocation(UUID.randomUUID(), companyId, r.name().trim(),
                r.latitude(), r.longitude(), r.radiusM())));
    }

    @PutMapping("/offices/{id}")
    @PreAuthorize("@perm.companyWide('ATTENDANCE_MANAGE')")
    @Transactional
    public Office updateOffice(@PathVariable UUID id, @Valid @RequestBody OfficeRequest r) {
        OfficeLocation o = offices.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Office not found"));
        o.update(r.name().trim(), r.latitude(), r.longitude(), r.radiusM());
        return Office.of(o);
    }

    /** Past check-ins keep the office name they were made at, so removing an office rewrites nothing. */
    @DeleteMapping("/offices/{id}")
    @PreAuthorize("@perm.companyWide('ATTENDANCE_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteOffice(@PathVariable UUID id) {
        OfficeLocation o = offices.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Office not found"));
        offices.delete(o);
    }

    /** Where each check-in and check-out happened on a day. Company-wide attendance managers only. */
    @GetMapping("/day")
    @PreAuthorize("@perm.companyWide('ATTENDANCE_MANAGE')")
    public List<Spot> day(@RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? attendanceService.companyToday() : LocalDate.parse(date);
        return punches.findByCompanyIdAndDateBetween(TenantContext.getCompanyId(), d, d).stream()
                .filter(p -> p.getInLatitude() != null || p.getOutLatitude() != null)
                .map(p -> new Spot(p.getEmployeeId().toString(),
                        p.getCheckIn() == null ? null : p.getCheckIn().toString(),
                        p.getCheckOut() == null ? null : p.getCheckOut().toString(),
                        p.getInLatitude(), p.getInLongitude(), p.getInAccuracyM(), p.getInOffice(), p.getInDistanceM(),
                        p.getOutLatitude(), p.getOutLongitude(), p.getOutAccuracyM(), p.getOutOffice(), p.getOutDistanceM()))
                .toList();
    }
}
