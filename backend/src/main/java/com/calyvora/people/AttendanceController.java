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

    public AttendanceController(AttendanceService attendanceService) {
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
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public AttendanceDayResponse day(@RequestParam(required = false) String date) {
        return attendanceService.day(date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date));
    }

    @PostMapping("/mark")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public AttendanceEntryResponse mark(@Valid @RequestBody MarkAttendanceRequest request,
                                        @CurrentUser AuthPrincipal principal) {
        return attendanceService.mark(request, principal);
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public AttendanceMonthResponse employeeMonth(@PathVariable UUID employeeId,
                                                 @RequestParam(required = false) String month) {
        return attendanceService.month(employeeId, parseMonth(month));
    }

    // ---- helpers ----

    private static YearMonth parseMonth(String month) {
        return month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
    }
}
