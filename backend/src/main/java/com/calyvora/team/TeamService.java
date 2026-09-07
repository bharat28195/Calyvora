package com.calyvora.team;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.expense.ExpenseClaim;
import com.calyvora.expense.ExpenseClaimRepository;
import com.calyvora.expense.ExpenseStatus;
import com.calyvora.expense.dto.ExpenseResponse;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.AttendanceRecord;
import com.calyvora.people.AttendanceRepository;
import com.calyvora.people.AttendanceStatus;
import com.calyvora.people.Department;
import com.calyvora.people.DepartmentRepository;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.LeaveRequest;
import com.calyvora.people.LeaveRequestRepository;
import com.calyvora.people.LeaveStatus;
import com.calyvora.people.OrgScope;
import com.calyvora.people.dto.LeaveRequestResponse;
import com.calyvora.performance.PerformanceReview;
import com.calyvora.performance.PerformanceReviewRepository;
import com.calyvora.team.dto.TeamMemberResponse;
import com.calyvora.team.dto.TeamSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "My team" — what somebody who leads people needs to know about them.
 *
 * <p>The section belongs to anyone with reports, not to the MANAGER role. A senior engineer with two
 * interns under them leads a team whatever their title says, and a MANAGER with nobody under them
 * leads none; keying this on {@link OrgScope} rather than on the role is what makes both true.
 *
 * <p><b>No pay, anywhere in this class.</b> Attendance, leave, expenses and performance are a team
 * lead's business. Salary is not, and it stays behind the HR screens — see {@code CompensationService}.
 * An expense claim is deliberately not counted as pay: it is money the person is owed back, which the
 * lead approving their trip has to be able to see.
 */
@Service
public class TeamService {

    private final OrgScope orgScope;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ExpenseClaimRepository expenseClaimRepository;
    private final PerformanceReviewRepository reviewRepository;

    public TeamService(OrgScope orgScope, EmployeeRepository employeeRepository, UserRepository userRepository,
                       DepartmentRepository departmentRepository, AttendanceRepository attendanceRepository,
                       LeaveRequestRepository leaveRequestRepository, ExpenseClaimRepository expenseClaimRepository,
                       PerformanceReviewRepository reviewRepository) {
        this.orgScope = orgScope;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.expenseClaimRepository = expenseClaimRepository;
        this.reviewRepository = reviewRepository;
    }

    /**
     * The people this caller may see on the team screens.
     *
     * <p>Self is excluded here, unlike {@link OrgScope#visibleEmployeeIds}: a roster of my team that
     * lists me among my own reports reads as an error, and my own attendance has its own section.
     */
    @Transactional(readOnly = true)
    public Set<UUID> rosterIds(AuthPrincipal principal, boolean directOnly) {
        return orgScope.downline(principal, directOnly);
    }

    @Transactional(readOnly = true)
    public TeamSummaryResponse summary(AuthPrincipal principal, boolean directOnly, YearMonth month) {
        UUID companyId = TenantContext.getCompanyId();
        Set<UUID> direct = orgScope.downline(principal, true);
        Set<UUID> everyone = orgScope.downline(principal, false);
        Set<UUID> roster = directOnly ? direct : everyone;

        List<Employee> all = employeeRepository.findByCompanyId(companyId);
        List<Employee> members = all.stream().filter(e -> roster.contains(e.getId())).toList();

        // Managers of the listed people include the viewer, who is not on the roster, so names are
        // resolved from the whole company rather than from the roster alone.
        Map<UUID, String> everyName = names(all);
        Map<UUID, String> departmentNames = departmentRepository.findByCompanyIdOrderByName(companyId).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        LocalDate today = LocalDate.now();
        Map<UUID, List<AttendanceRecord>> attendance = attendanceRepository
                .findByCompanyIdAndDateBetween(companyId, month.atDay(1), month.atEndOfMonth()).stream()
                .filter(r -> roster.contains(r.getEmployeeId()))
                .collect(Collectors.groupingBy(AttendanceRecord::getEmployeeId));
        Map<UUID, AttendanceStatus> todayStatus = attendanceRepository
                .findByCompanyIdAndDate(companyId, today).stream()
                .filter(r -> roster.contains(r.getEmployeeId()))
                .collect(Collectors.toMap(AttendanceRecord::getEmployeeId, AttendanceRecord::getStatus, (a, b) -> a));

        Map<UUID, Long> pendingLeave = leaveRequestRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(r -> roster.contains(r.getEmployeeId()) && r.getStatus() == LeaveStatus.PENDING)
                .collect(Collectors.groupingBy(LeaveRequest::getEmployeeId, Collectors.counting()));

        List<ExpenseClaim> openClaims = expenseClaimRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(c -> roster.contains(c.getEmployeeId()))
                .filter(c -> c.getStatus() == ExpenseStatus.SUBMITTED || c.getStatus() == ExpenseStatus.APPROVED)
                .toList();
        Map<UUID, Long> openExpenseCount = openClaims.stream()
                .collect(Collectors.groupingBy(ExpenseClaim::getEmployeeId, Collectors.counting()));
        Map<UUID, BigDecimal> openExpenseAmount = new HashMap<>();
        for (ExpenseClaim c : openClaims) {
            openExpenseAmount.merge(c.getEmployeeId(),
                    c.getAmount() == null ? BigDecimal.ZERO : c.getAmount(), BigDecimal::add);
        }

        // One query for the whole roster, newest first, keeping the first status seen per person —
        // the alternative is a query per report, which is thirty round trips to draw one table.
        Map<UUID, String> latestReview = new HashMap<>();
        if (!roster.isEmpty()) {
            for (PerformanceReview r : reviewRepository.findByEmployeeIdInOrderByCreatedAtDesc(roster)) {
                latestReview.putIfAbsent(r.getEmployeeId(), r.getStatus().name());
            }
        }

        List<TeamMemberResponse> rows = new ArrayList<>();
        for (Employee e : members) {
            List<AttendanceRecord> records = attendance.getOrDefault(e.getId(), List.of());
            AttendanceStatus todays = todayStatus.get(e.getId());
            rows.add(new TeamMemberResponse(
                    e.getId().toString(),
                    e.getUserId() == null ? null : e.getUserId().toString(),
                    everyName.getOrDefault(e.getId(), "Unknown"),
                    e.getJobTitle(),
                    e.getDepartmentId() == null ? null : departmentNames.get(e.getDepartmentId()),
                    e.getManagerId() == null ? null : everyName.get(e.getManagerId()),
                    direct.contains(e.getId()),
                    direct.contains(e.getId()) ? 1 : 2,
                    e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name(),
                    todays == null ? null : todays.name(),
                    count(records, AttendanceStatus.PRESENT, AttendanceStatus.WORK_FROM_HOME, AttendanceStatus.HALF_DAY),
                    count(records, AttendanceStatus.ABSENT),
                    count(records, AttendanceStatus.ON_LEAVE),
                    pendingLeave.getOrDefault(e.getId(), 0L),
                    openExpenseCount.getOrDefault(e.getId(), 0L),
                    openExpenseAmount.getOrDefault(e.getId(), BigDecimal.ZERO),
                    e.getRating(),
                    latestReview.get(e.getId())));
        }
        // Direct reports first, then the rest of the org alphabetically: the people whose leave a lead
        // actually decides should not be interleaved with the ones they merely oversee.
        rows.sort(Comparator.comparing(TeamMemberResponse::direct).reversed()
                .thenComparing(r -> r.name() == null ? "" : r.name(), String.CASE_INSENSITIVE_ORDER));

        long presentToday = todayStatus.values().stream()
                .filter(s -> s == AttendanceStatus.PRESENT || s == AttendanceStatus.WORK_FROM_HOME
                        || s == AttendanceStatus.HALF_DAY)
                .count();
        long onLeaveToday = todayStatus.values().stream().filter(s -> s == AttendanceStatus.ON_LEAVE).count();

        return new TeamSummaryResponse(month.toString(), direct.size(), everyone.size(),
                presentToday, onLeaveToday,
                pendingLeave.values().stream().mapToLong(Long::longValue).sum(),
                openClaims.size(), rows);
    }

    /** Leave requests across the team, newest first. */
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> leave(AuthPrincipal principal, boolean directOnly) {
        UUID companyId = TenantContext.getCompanyId();
        Set<UUID> roster = rosterIds(principal, directOnly);
        Map<UUID, String> names = names(employeeRepository.findByCompanyId(companyId));
        return leaveRequestRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(r -> roster.contains(r.getEmployeeId()))
                .map(r -> LeaveRequestResponse.of(r, names.getOrDefault(r.getEmployeeId(), "Unknown")))
                .toList();
    }

    /** Expense claims across the team. */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> expenses(AuthPrincipal principal, boolean directOnly) {
        UUID companyId = TenantContext.getCompanyId();
        Set<UUID> roster = rosterIds(principal, directOnly);
        Map<UUID, String> names = names(employeeRepository.findByCompanyId(companyId));
        return expenseClaimRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(c -> roster.contains(c.getEmployeeId()))
                .map(c -> ExpenseResponse.of(c, names.getOrDefault(c.getEmployeeId(), "Unknown")))
                .toList();
    }

    /** Whether this employee is on the caller's team — the check every per-person team route makes. */
    @Transactional(readOnly = true)
    public boolean isOnRoster(AuthPrincipal principal, UUID employeeId, boolean directOnly) {
        return rosterIds(principal, directOnly).contains(employeeId);
    }

    // ---------- helpers ----------

    private static long count(List<AttendanceRecord> records, AttendanceStatus... wanted) {
        Set<AttendanceStatus> set = Set.of(wanted);
        return records.stream().filter(r -> set.contains(r.getStatus())).count();
    }

    /** Employee id to display name, for a batch of employees, in one user query. */
    private Map<UUID, String> names(List<Employee> employees) {
        List<UUID> userIds = employees.stream().map(Employee::getUserId).filter(Objects::nonNull).toList();
        Map<UUID, String> byUser = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::fullName, (a, b) -> a));
        return employees.stream()
                .filter(e -> e.getUserId() != null && byUser.containsKey(e.getUserId()))
                .collect(Collectors.toMap(Employee::getId, e -> byUser.get(e.getUserId()), (a, b) -> a));
    }
}
