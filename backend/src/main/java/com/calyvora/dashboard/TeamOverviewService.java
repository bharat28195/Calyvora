package com.calyvora.dashboard;

import com.calyvora.common.security.TenantContext;
import com.calyvora.dashboard.dto.TeamOverviewResponse;
import com.calyvora.dashboard.dto.TeamOverviewResponse.CalendarLeave;
import com.calyvora.dashboard.dto.TeamOverviewResponse.LeaveToday;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.people.EmployeeService;
import com.calyvora.people.LeaveRequest;
import com.calyvora.people.LeaveRequestRepository;
import com.calyvora.people.LeaveStatus;
import com.calyvora.people.dto.EmployeeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the Owner/Admin team overview (attendance derived from approved leave — phase 1). Tenant-scoped.
 */
@Service
public class TeamOverviewService {

    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRepository;
    private final EmployeeService employeeService;

    public TeamOverviewService(UserRepository userRepository, LeaveRequestRepository leaveRepository,
                               EmployeeService employeeService) {
        this.userRepository = userRepository;
        this.leaveRepository = leaveRepository;
        this.employeeService = employeeService;
    }

    @Transactional(readOnly = true)
    public TeamOverviewResponse overview() {
        UUID companyId = TenantContext.getCompanyId();
        long headcount = userRepository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE);

        // employee id -> display name
        Map<String, String> names = new LinkedHashMap<>();
        for (EmployeeResponse e : employeeService.directory()) {
            names.put(e.id(), (e.firstName() + " " + e.lastName()).trim());
        }

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        List<LeaveToday> outToday = new ArrayList<>();
        List<CalendarLeave> monthLeaves = new ArrayList<>();

        for (LeaveRequest lr : leaveRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            boolean counted = lr.getStatus() == LeaveStatus.APPROVED;
            boolean visible = counted || lr.getStatus() == LeaveStatus.PENDING;
            if (!visible) {
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

        long onLeaveToday = outToday.size();
        long present = Math.max(0, headcount - onLeaveToday);
        return new TeamOverviewResponse(headcount, present, onLeaveToday, outToday, monthLeaves);
    }
}
