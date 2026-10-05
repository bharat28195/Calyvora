package com.calyvora.analytics;

import com.calyvora.analytics.dto.AnalyticsOverviewResponse;
import com.calyvora.analytics.dto.AnalyticsOverviewResponse.Finance;
import com.calyvora.analytics.dto.AnalyticsOverviewResponse.People;
import com.calyvora.analytics.dto.AnalyticsOverviewResponse.Slice;
import com.calyvora.common.security.TenantContext;
import com.calyvora.expense.ExpenseClaim;
import com.calyvora.expense.ExpenseClaimRepository;
import com.calyvora.expense.ExpenseStatus;
import com.calyvora.people.Department;
import com.calyvora.people.DepartmentRepository;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.EmploymentStatus;
import com.calyvora.people.Goal;
import com.calyvora.people.GoalRepository;
import com.calyvora.people.GoalStatus;
import com.calyvora.people.LeaveRequest;
import com.calyvora.people.LeaveRequestRepository;
import com.calyvora.people.LeaveStatus;
import com.calyvora.people.LeaveType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Company-wide analytics (Insights dashboard, Owner/Admin). Reaches across People and Finance
 * and returns chart-ready series. Everything is derived from data we actually store: headcount growth
 * comes from employee start dates, velocity from completed sprints, and so on — nothing is fabricated,
 * so an empty company yields empty series rather than invented numbers.
 */
@Service
public class AnalyticsService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveRequestRepository leaveRepository;
    private final GoalRepository goalRepository;
    private final ExpenseClaimRepository expenseRepository;

    public AnalyticsService(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                            LeaveRequestRepository leaveRepository, GoalRepository goalRepository,
                            ExpenseClaimRepository expenseRepository) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.leaveRepository = leaveRepository;
        this.goalRepository = goalRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview() {
        UUID companyId = TenantContext.getCompanyId();
        return new AnalyticsOverviewResponse(people(companyId), finance(companyId));
    }

    // ---------------- People ----------------

    private People people(UUID companyId) {
        List<Employee> employees = employeeRepository.findByCompanyId(companyId).stream()
                .filter(e -> e.getEmploymentStatus() == EmploymentStatus.ACTIVE)
                .toList();
        LocalDate today = LocalDate.now();

        // Headcount by department (name resolved; anyone without a department bucketed as "Unassigned").
        Map<UUID, String> deptNames = new java.util.HashMap<>();
        for (Department d : departmentRepository.findByCompanyIdOrderByName(companyId)) {
            deptNames.put(d.getId(), d.getName());
        }
        Map<String, Long> byDept = new java.util.LinkedHashMap<>();
        for (Employee e : employees) {
            String name = e.getDepartmentId() == null ? "Unassigned"
                    : deptNames.getOrDefault(e.getDepartmentId(), "Unassigned");
            byDept.merge(name, 1L, Long::sum);
        }

        // Cumulative headcount at the end of each of the last 12 months, from start dates.
        List<Slice> growth = new ArrayList<>();
        long newJoiners = 0;
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = YearMonth.from(today).minusMonths(i);
            LocalDate monthEnd = ym.atEndOfMonth();
            long count = employees.stream()
                    .filter(e -> e.getStartDate() != null && !e.getStartDate().isAfter(monthEnd))
                    .count();
            growth.add(new Slice(ym.toString(), count));
        }
        double totalTenureMonths = 0;
        int tenured = 0;
        for (Employee e : employees) {
            if (e.getStartDate() == null) continue;
            if (e.getStartDate().getYear() == today.getYear()) newJoiners++;
            totalTenureMonths += monthsBetween(e.getStartDate(), today);
            tenured++;
        }
        double avgTenure = tenured == 0 ? 0 : round1(totalTenureMonths / tenured);

        // Ratings 1..5.
        List<Slice> ratings = new ArrayList<>();
        for (int r = 1; r <= 5; r++) {
            final int rr = r;
            long c = employees.stream().filter(e -> e.getRating() != null && e.getRating() == rr).count();
            ratings.add(new Slice(r + "★", c));
        }

        // Approved leave days this year, by type.
        int year = today.getYear();
        Map<LeaveType, Double> leaveDays = new EnumMap<>(LeaveType.class);
        long onLeaveToday = 0;
        for (LeaveRequest lr : leaveRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            if (lr.getStatus() != LeaveStatus.APPROVED) continue;
            if (lr.getStartDate().getYear() == year || lr.getEndDate().getYear() == year) {
                leaveDays.merge(lr.getType(), (double) lr.getDays(), Double::sum);
            }
            if (!today.isBefore(lr.getStartDate()) && !today.isAfter(lr.getEndDate())) onLeaveToday++;
        }
        List<Slice> leaveByType = new ArrayList<>();
        for (LeaveType t : LeaveType.values()) {
            leaveByType.add(new Slice(title(t.name()), leaveDays.getOrDefault(t, 0.0)));
        }

        // Goals.
        List<Goal> goals = goalRepository.findByCompanyId(companyId);
        long open = goals.stream().filter(g -> g.getStatus() == GoalStatus.OPEN).count();
        long achieved = goals.stream().filter(g -> g.getStatus() == GoalStatus.ACHIEVED).count();
        long missed = goals.stream().filter(g -> g.getStatus() == GoalStatus.MISSED).count();
        double avgProgress = goals.isEmpty() ? 0
                : round1(goals.stream().mapToInt(Goal::getProgress).average().orElse(0));

        return new People(employees.size(), newJoiners, avgTenure, onLeaveToday,
                open, achieved, missed, avgProgress,
                slices(byDept), growth, ratings, leaveByType);
    }

    // ---------------- Finance ----------------

    private Finance finance(UUID companyId) {
        List<ExpenseClaim> claims = expenseRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        int year = LocalDate.now().getYear();
        double pending = 0, awaiting = 0, reimbursed = 0;
        String currency = "INR";
        Map<String, Double> byCategory = new java.util.LinkedHashMap<>();
        for (ExpenseClaim c : claims) {
            currency = c.getCurrency();
            double amt = c.getAmount() == null ? 0 : c.getAmount().doubleValue();
            switch (c.getStatus()) {
                case SUBMITTED -> pending += amt;
                case APPROVED -> awaiting += amt;
                case REIMBURSED -> {
                    if (c.getReimbursedAt() != null
                            && c.getReimbursedAt().atZone(java.time.ZoneOffset.UTC).getYear() == year) {
                        reimbursed += amt;
                    }
                }
                case REJECTED -> { /* not counted */ }
            }
            if (c.getStatus() != ExpenseStatus.REJECTED) {
                byCategory.merge(title(c.getCategory().name()), amt, Double::sum);
            }
        }
        return new Finance(currency, round2(pending), round2(awaiting), round2(reimbursed), slices(byCategory));
    }

    // ---------------- helpers ----------------

    private static List<Slice> slices(Map<String, ? extends Number> map) {
        List<Slice> out = new ArrayList<>();
        map.forEach((k, v) -> out.add(new Slice(k, v.doubleValue())));
        return out;
    }

    private static long monthsBetween(LocalDate from, LocalDate to) {
        return java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to));
    }

    private static String title(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100) / 100.0; }
}
