package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.AttendanceDayResponse;
import com.calyvora.people.dto.AttendanceEntryResponse;
import com.calyvora.people.dto.AttendanceMonthResponse;
import com.calyvora.people.dto.MarkAttendanceRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Daily attendance (feedback C.4). Everyone can clock themselves in/out and see their own month;
 * the team day sheet and marking someone else's day are Owner/Admin.
 */
@RestController
@RequestMapping("/api/v1/people/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final OrgScope orgScope;
    private final com.calyvora.access.PermissionService permissions;

    public AttendanceController(AttendanceService attendanceService, OrgScope orgScope,
                                com.calyvora.access.PermissionService permissions) {
        this.orgScope = orgScope;
        this.permissions = permissions;
        this.attendanceService = attendanceService;
    }

    // ---- self-service ----

    @GetMapping("/me/today")
    public AttendanceEntryResponse today(@CurrentUser AuthPrincipal principal) {
        return attendanceService.today(principal);
    }

    @PostMapping("/me/check-in")
    public AttendanceEntryResponse checkIn(@CurrentUser AuthPrincipal principal) {
        return attendanceService.checkIn(principal);
    }

    @PostMapping("/me/check-out")
    public AttendanceEntryResponse checkOut(@CurrentUser AuthPrincipal principal) {
        return attendanceService.checkOut(principal);
    }

    /** Clear today's clock-in/out (so the day is open again). */
    @org.springframework.web.bind.annotation.DeleteMapping("/me/today")
    public AttendanceEntryResponse resetToday(@CurrentUser AuthPrincipal principal) {
        return attendanceService.clearToday(principal);
    }

    @GetMapping("/me")
    public AttendanceMonthResponse myMonth(@RequestParam(required = false) String month,
                                           @CurrentUser AuthPrincipal principal) {
        return attendanceService.month(attendanceService.myEmployeeId(principal), parseMonth(month));
    }

    // ---- team ----

    @GetMapping("/day")
    @PreAuthorize("@perm.companyWide('ATTENDANCE_MANAGE')")
    public AttendanceDayResponse day(@RequestParam(required = false) String date) {
        return attendanceService.day(date == null || date.isBlank()
                ? attendanceService.companyToday() : LocalDate.parse(date));
    }

    /**
     * Correct many days at once, applied immediately (V70). HR and admins reach anyone; a lead
     * reaches only their own downline (PD-32), and one person outside it refuses the whole request
     * rather than applying part of it.
     */
    @PostMapping("/correct")
    @PreAuthorize("@perm.has('ATTENDANCE_MANAGE')")
    public java.util.List<AttendanceEntryResponse> correct(
            @Valid @RequestBody com.calyvora.people.dto.CorrectAttendanceRequest request,
            @CurrentUser AuthPrincipal principal) {
        java.util.List<UUID> employeeIds;
        java.util.List<LocalDate> dates;
        try {
            employeeIds = request.employeeIds().stream().map(UUID::fromString).toList();
            dates = request.dates().stream().map(LocalDate::parse).toList();
        } catch (RuntimeException bad) {
            throw new com.calyvora.common.error.ApiException(
                    com.calyvora.common.error.ErrorCode.VALIDATION_ERROR, "Invalid person or date");
        }
        if (!permissions.companyWide(principal, com.calyvora.access.Permission.ATTENDANCE_MANAGE)) {
            java.util.Set<UUID> mine = orgScope.downline(principal, false);
            if (!mine.containsAll(employeeIds)) {
                throw new com.calyvora.common.error.ApiException(
                        com.calyvora.common.error.ErrorCode.FORBIDDEN,
                        "You can only correct attendance for people in your team");
            }
        }
        return attendanceService.correctMany(employeeIds, dates,
                com.calyvora.people.AttendanceStatus.valueOf(request.status()),
                request.checkIn(), request.checkOut(), request.reason(), principal);
    }

    /**
     * The month behind a calendar grid: one row per day, counts only.
     *
     * <p>Scoped by the reporting tree (PD-32), like the team overview: the whole company for the
     * roles that see everyone, their own downline for a lead. Not restricted to Owner/Admin — the
     * lead looking at their team's month is the person most likely to want it.
     */
    @GetMapping("/month-summary")
    public com.calyvora.people.dto.AttendanceMonthSummaryResponse monthSummary(
            @RequestParam(required = false) String month,
            @CurrentUser AuthPrincipal principal) {
        java.util.Set<UUID> scope = null;
        if (!orgScope.seesWholeCompany(principal)) {
            scope = orgScope.downline(principal, false);
            if (scope.isEmpty()) {
                throw new com.calyvora.common.error.ApiException(
                        com.calyvora.common.error.ErrorCode.FORBIDDEN,
                        "You do not have permission to perform this action");
            }
        }
        return attendanceService.monthSummary(parseMonth(month), scope);
    }

    @PostMapping("/mark")
    @PreAuthorize("@perm.companyWide('ATTENDANCE_MANAGE')")
    public AttendanceEntryResponse mark(@Valid @RequestBody MarkAttendanceRequest request,
                                        @CurrentUser AuthPrincipal principal) {
        return attendanceService.mark(request, principal);
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("@perm.companyWide('ATTENDANCE_MANAGE')")
    public AttendanceMonthResponse employeeMonth(@PathVariable UUID employeeId,
                                                 @RequestParam(required = false) String month) {
        return attendanceService.month(employeeId, parseMonth(month));
    }

    // ---- helpers ----

    private static YearMonth parseMonth(String month) {
        return month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
    }
}
