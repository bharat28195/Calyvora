package com.calyvora.dashboard;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.dashboard.dto.TeamOverviewResponse;
import com.calyvora.dashboard.dto.TeamOverviewResponse.CalendarLeave;
import com.calyvora.dashboard.dto.TeamOverviewResponse.LeaveToday;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.people.AttendanceService;
import com.calyvora.people.EmployeeService;
import com.calyvora.people.LeaveRequest;
import com.calyvora.people.dto.AttendanceDayResponse;
import com.calyvora.people.LeaveRequestRepository;
import com.calyvora.people.LeaveStatus;
import com.calyvora.people.OrgScope;
import com.calyvora.people.dto.EmployeeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the team overview (attendance derived from approved leave — phase 1). Tenant-scoped.
 *
 * <p>Was Owner/Admin only. Under PD-32 the reporting tree grants visibility, so a lead gets the
 * same panel over their own downline: the one screen a manager opens every morning is "who is in
 * today", and it made no sense that the role which needs it most was the one that could not have it.
 */
@Service
public class TeamOverviewService {

    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRepository;
    private final EmployeeService employeeService;
    private final AttendanceService attendanceService;
    private final OrgScope orgScope;

    public TeamOverviewService(UserRepository userRepository, LeaveRequestRepository leaveRepository,
                               EmployeeService employeeService, AttendanceService attendanceService,
                               OrgScope orgScope) {
        this.userRepository = userRepository;
        this.leaveRepository = leaveRepository;
        this.employeeService = employeeService;
        this.attendanceService = attendanceService;
        this.orgScope = orgScope;
    }

    @Transactional(readOnly = true)
    public TeamOverviewResponse overview(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();

        // Null means "everyone"; a set means "these people only". The whole-company roles see the
        // headcount of active users, which is the number on the People page; a lead's headcount is
        // the size of their downline, which is what "my team" means to them.
        Set<UUID> scope = null;
        long headcount;
        if (orgScope.seesWholeCompany(principal)) {
            headcount = userRepository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE);
        } else {
            scope = orgScope.downline(principal, false);
            if (scope.isEmpty()) {
                throw new ApiException(ErrorCode.FORBIDDEN, "You do not have permission to perform this action");
            }
            headcount = scope.size();
        }

        // employee id -> display name, for the people in scope
        Map<String, String> names = new LinkedHashMap<>();
        for (EmployeeResponse e : employeeService.directory()) {
            if (scope == null || scope.contains(UUID.fromString(e.id()))) {
                names.put(e.id(), (e.firstName() + " " + e.lastName()).trim());
            }
        }

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        List<LeaveToday> outToday = new ArrayList<>();
        List<CalendarLeave> monthLeaves = new ArrayList<>();

        for (LeaveRequest lr : leaveRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            boolean counted = lr.getStatus() == LeaveStatus.APPROVED;
            boolean visible = counted || lr.getStatus() == LeaveStatus.PENDING;
            if (!visible || (scope != null && !scope.contains(lr.getEmployeeId()))) {
                continue;
            }
            String name = names.getOrDefault(lr.getEmployeeId().toString(), "Someone");

            // out today (approved leave covering today)
            if (counted && !today.isBefore(lr.getStartDate()) && !today.isAfter(lr.getEndDate())) {
                outToday.add(new LeaveToday(name, lr.getType().name(), lr.getReason(),
                        lr.getStartDate().toString(), lr.getEndDate().toString()));
            }

            // overlaps the current month → include for the calendar
            if (!lr.getStartDate().isAfter(monthEnd) && !lr.getEndDate().isBefore(monthStart)) {
                monthLeaves.add(new CalendarLeave(name, lr.getType().name(), lr.getStatus().name(),
                        lr.getStartDate().toString(), lr.getEndDate().toString()));
            }
        }

        // The day sheet already resolves each person: a marked row wins, an unmarked day falls back to
        // approved leave. Unmarked people are still counted as in (the phase-1 assumption), but we
        // report how many that is — so the owner can see how much of "present" is assumed.
        AttendanceDayResponse sheet = attendanceService.day(today);
        if (scope == null) {
            return new TeamOverviewResponse(headcount, sheet.present() + sheet.unmarked(), sheet.onLeave(),
                    sheet.unmarked(), outToday, monthLeaves);
        }
        // The sheet is company-wide; a lead's counts come from their own rows on it.
        long present = 0;
        long onLeave = 0;
        long unmarked = 0;
        for (var entry : sheet.entries()) {
            if (!scope.contains(UUID.fromString(entry.employeeId()))) {
                continue;
            }
            if (entry.status() == null) {
                unmarked++;
                continue;
            }
            var s = com.calyvora.people.AttendanceStatus.valueOf(entry.status());
            if (s.isWorking()) present++;
            else if (s == com.calyvora.people.AttendanceStatus.ON_LEAVE) onLeave++;
        }
        return new TeamOverviewResponse(headcount, present + unmarked, onLeave, unmarked, outToday, monthLeaves);
    }
}
