package com.calyvora.analytics;

import com.calyvora.analytics.dto.ProfitabilityResponse;
import com.calyvora.analytics.dto.ProfitabilityResponse.PersonLine;
import com.calyvora.analytics.dto.ProfitabilityResponse.ProjectLine;
import com.calyvora.client.Client;
import com.calyvora.client.ClientRepository;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import com.calyvora.company.CompanyZone;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.AttendanceRecord;
import com.calyvora.people.AttendanceRepository;
import com.calyvora.people.AttendanceStatus;
import com.calyvora.people.CompensationRecord;
import com.calyvora.people.CompensationRepository;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.EmploymentStatus;
import com.calyvora.work.Project;
import com.calyvora.work.ProjectRepository;
import com.calyvora.work.Task;
import com.calyvora.work.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Project and per-person profitability (PD-22, D2) — what delivery cost, and what it was sold for.
 *
 * <p>The join is the product: compensation says what people cost, attendance says how much of the
 * period they worked, Work says what they delivered and where, and Client says who it was for. No
 * competitor holds all four on one schema, so none of them can answer this without an integration
 * project.
 *
 * <h2>How the number is built, and what it is not</h2>
 * <ol>
 *   <li><b>Cost pool per person</b> = (current annual pay ÷ {@value #WORKING_DAYS_PER_YEAR}) × days
 *       worked in the period. Days worked come from attendance; half days count as half, leave and
 *       holidays do not count. If a company records no attendance at all, we fall back to weekdays
 *       in the period rather than reporting everyone as free.</li>
 *   <li><b>Allocation</b> spreads that pool across the projects they closed work in, weighted by
 *       story points — or by item count when nothing they closed was estimated.</li>
 *   <li><b>Margin</b> = agreed fee − allocated cost, and only where a fee has been agreed.</li>
 * </ol>
 *
 * <p><b>This is an allocation model, not a timesheet.</b> It answers "where did our payroll go" with
 * the evidence we have, and it will be wrong for anyone whose valuable work is not tracked as closed
 * items. Cost belonging to people who delivered nothing trackable is reported as
 * {@code unallocatedCost} rather than being quietly spread over the projects — hiding it would make
 * every project look cheaper than it was.
 */
@Service
public class ProfitabilityService {

    /** Working days in a year, the conventional 52 × 5 with a fortnight of leave. */
    private static final int WORKING_DAYS_PER_YEAR = 250;

    private static final String BASIS =
            "Cost is allocated, not measured: each person's pay for the period is spread across the "
            + "projects they closed work in, weighted by story points (or item count when unestimated). "
            + "Work that isn't tracked as closed items won't appear here.";

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CompensationRepository compensationRepository;
    private final AttendanceRepository attendanceRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final CompanyZone companyZone;

    public ProfitabilityService(EmployeeRepository employeeRepository, UserRepository userRepository,
                                CompensationRepository compensationRepository,
                                AttendanceRepository attendanceRepository, TaskRepository taskRepository,
                                ProjectRepository projectRepository, ClientRepository clientRepository,
                                CompanySettingsRepository companySettingsRepository, CompanyZone companyZone) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.compensationRepository = compensationRepository;
        this.attendanceRepository = attendanceRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.companyZone = companyZone;
    }

    @Transactional(readOnly = true)
    public ProfitabilityResponse forPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Give a period with the end on or after the start");
        }
        UUID companyId = TenantContext.getCompanyId();
        String currency = companySettingsRepository.findById(companyId)
                .map(CompanySettings::getCurrency).filter(c -> c != null && !c.isBlank()).orElse("INR");

        Map<UUID, Project> projects = new HashMap<>();
        for (Project p : projectRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            projects.put(p.getId(), p);
        }
        Map<UUID, String> clientNames = new HashMap<>();
        for (Client c : clientRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            clientNames.put(c.getId(), c.getName());
        }

        // Delivered work in the window, grouped by who closed it and where.
        Instant start = companyZone.startOfDay(from);
        Instant end = companyZone.endOfDayExclusive(to);
        Map<UUID, Map<UUID, int[]>> deliveredBy = new HashMap<>();   // employee -> project -> [items, points]
        for (Task t : taskRepository.findByCompanyIdAndCompletedAtBetween(companyId, start, end)) {
            if (t.getAssigneeId() == null || !projects.containsKey(t.getProjectId())) {
                continue;   // unassigned work has no one to cost it to
            }
            int[] tally = deliveredBy
                    .computeIfAbsent(t.getAssigneeId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(t.getProjectId(), k -> new int[2]);
            tally[0]++;
            tally[1] += t.getStoryPoints() == null ? 0 : t.getStoryPoints();
        }

        Map<UUID, BigDecimal> currentPay = currentPayByEmployee(companyId);

        // The agreed fee covers the whole engagement, so margin has to be measured against the whole
        // engagement's cost. Comparing a year's fee to one month's spend would report a ~100% margin
        // on every young project. The second pass runs from the oldest project's creation date —
        // bounded, rather than from the beginning of time, so the no-attendance fallback stays sane.
        Map<UUID, BigDecimal> costToDate = projects.isEmpty() ? Map.of()
                : allocate(companyId, earliestProjectDate(projects.values(), from), to, projects, currentPay)
                        .costByProject();

        Allocation period = allocate(companyId, from, to, projects, currentPay);

        return assemble(from, to, currency, projects, clientNames, period, costToDate);
    }

    /** The result of spreading one window's payroll across the work it produced. */
    private record Allocation(Map<UUID, BigDecimal> costByProject,
                              Map<UUID, Set<UUID>> contributorsByProject,
                              Map<UUID, int[]> deliveryByProject,
                              List<PersonLine> people,
                              BigDecimal unallocated) {
    }

    private Allocation allocate(UUID companyId, LocalDate from, LocalDate to,
                                Map<UUID, Project> projects, Map<UUID, BigDecimal> currentPay) {
        Instant start = companyZone.startOfDay(from);
        Instant end = companyZone.endOfDayExclusive(to);
        Map<UUID, Map<UUID, int[]>> deliveredBy = new HashMap<>();   // employee -> project -> [items, points]
        for (Task t : taskRepository.findByCompanyIdAndCompletedAtBetween(companyId, start, end)) {
            if (t.getAssigneeId() == null || !projects.containsKey(t.getProjectId())) {
                continue;   // unassigned work has no one to cost it to
            }
            int[] tally = deliveredBy
                    .computeIfAbsent(t.getAssigneeId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(t.getProjectId(), k -> new int[2]);
            tally[0]++;
            tally[1] += t.getStoryPoints() == null ? 0 : t.getStoryPoints();
        }

        long weekdays = weekdaysBetween(from, to);
        Map<UUID, BigDecimal> costByProject = new HashMap<>();
        Map<UUID, Set<UUID>> contributorsByProject = new HashMap<>();
        List<PersonLine> people = new ArrayList<>();
        BigDecimal unallocated = BigDecimal.ZERO;

        for (Employee employee : employeeRepository.findByCompanyId(companyId)) {
            if (employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
                continue;
            }
            BigDecimal annual = currentPay.get(employee.getId());
            if (annual == null || annual.signum() <= 0) {
                continue;   // no pay on record — costing them zero beats inventing a salary
            }
            double daysWorked = daysWorked(employee.getId(), from, to, weekdays);
            if (daysWorked <= 0) {
                continue;
            }
            BigDecimal cost = annual
                    .divide(BigDecimal.valueOf(WORKING_DAYS_PER_YEAR), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(daysWorked))
                    .setScale(2, RoundingMode.HALF_UP);

            Map<UUID, int[]> delivered = deliveredBy.getOrDefault(employee.getId(), Map.of());
            if (delivered.isEmpty()) {
                // They cost money and closed nothing trackable. Say so rather than inflating projects.
                unallocated = unallocated.add(cost);
                people.add(personLine(employee, cost, BigDecimal.ZERO, daysWorked, 0, 0, 0));
                continue;
            }

            long totalPoints = delivered.values().stream().mapToLong(v -> v[1]).sum();
            long totalItems = delivered.values().stream().mapToLong(v -> v[0]).sum();
            boolean byPoints = totalPoints > 0;
            long denominator = byPoints ? totalPoints : totalItems;

            BigDecimal billableCost = BigDecimal.ZERO;
            BigDecimal spread = BigDecimal.ZERO;
            List<Map.Entry<UUID, int[]>> entries = new ArrayList<>(delivered.entrySet());
            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<UUID, int[]> e = entries.get(i);
                long weight = byPoints ? e.getValue()[1] : e.getValue()[0];
                // Give the last project the remainder so allocations always sum back to the pool.
                BigDecimal share = (i == entries.size() - 1)
                        ? cost.subtract(spread)
                        : cost.multiply(BigDecimal.valueOf(weight))
                              .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
                spread = spread.add(share);

                costByProject.merge(e.getKey(), share, BigDecimal::add);
                contributorsByProject.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(employee.getId());
                Project p = projects.get(e.getKey());
                if (p != null && p.isBillable()) {
                    billableCost = billableCost.add(share);
                }
            }

            BigDecimal billablePercent = cost.signum() == 0 ? BigDecimal.ZERO
                    : billableCost.multiply(BigDecimal.valueOf(100)).divide(cost, 1, RoundingMode.HALF_UP);
            people.add(personLine(employee, cost, billablePercent, daysWorked,
                    (int) totalItems, (int) totalPoints, delivered.size()));
        }

        Map<UUID, int[]> deliveryByProject = new HashMap<>();
        for (Map<UUID, int[]> perProject : deliveredBy.values()) {
            perProject.forEach((projectId, v) -> {
                int[] tally = deliveryByProject.computeIfAbsent(projectId, k -> new int[2]);
                tally[0] += v[0];
                tally[1] += v[1];
            });
        }
        return new Allocation(costByProject, contributorsByProject, deliveryByProject, people, unallocated);
    }

    // ---- assembly ----

    private ProfitabilityResponse assemble(LocalDate from, LocalDate to, String currency,
                                           Map<UUID, Project> projects, Map<UUID, String> clientNames,
                                           Allocation period, Map<UUID, BigDecimal> costToDate) {
        List<ProjectLine> lines = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalContract = BigDecimal.ZERO;
        BigDecimal totalMargin = BigDecimal.ZERO;

        for (Map.Entry<UUID, BigDecimal> entry : period.costByProject().entrySet()) {
            Project p = projects.get(entry.getKey());
            if (p == null) continue;
            BigDecimal cost = entry.getValue();
            // The fee covers the engagement, so it is measured against the engagement's whole cost —
            // never against this period's slice of it.
            BigDecimal lifetime = costToDate.getOrDefault(p.getId(), cost);
            BigDecimal fee = p.getContractValue();
            BigDecimal margin = fee == null ? null : fee.subtract(lifetime);
            BigDecimal marginPercent = (fee == null || fee.signum() == 0) ? null
                    : margin.multiply(BigDecimal.valueOf(100)).divide(fee, 1, RoundingMode.HALF_UP);
            int[] delivery = period.deliveryByProject().getOrDefault(p.getId(), new int[2]);

            lines.add(new ProjectLine(
                    p.getId().toString(), p.getKey(), p.getName(),
                    p.getClientId() == null ? null : p.getClientId().toString(),
                    p.getClientId() == null ? null : clientNames.get(p.getClientId()),
                    p.isBillable(), fee, cost, lifetime, margin, marginPercent,
                    delivery[0], delivery[1],
                    period.contributorsByProject().getOrDefault(p.getId(), Set.of()).size()));

            totalCost = totalCost.add(cost);
            if (fee != null) {
                totalContract = totalContract.add(fee);
                totalMargin = totalMargin.add(margin);
            }
        }

        // Most expensive first — the question is always "where is the money going".
        lines.sort((a, b) -> b.cost().compareTo(a.cost()));
        List<PersonLine> people = new ArrayList<>(period.people());
        people.sort((a, b) -> b.cost().compareTo(a.cost()));

        BigDecimal grandCost = totalCost.add(period.unallocated());
        return new ProfitabilityResponse(from.toString(), to.toString(), currency, BASIS,
                grandCost, totalContract, totalMargin,
                lines.size(), people.size(), period.unallocated(), lines, people);
    }

    /** Oldest project creation date, bounding the cost-to-date pass so it never scans from epoch. */
    private static LocalDate earliestProjectDate(java.util.Collection<Project> projects, LocalDate fallback) {
        LocalDate earliest = fallback;
        for (Project p : projects) {
            LocalDate created = p.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
            if (created.isBefore(earliest)) {
                earliest = created;
            }
        }
        return earliest;
    }

    private PersonLine personLine(Employee employee, BigDecimal cost, BigDecimal billablePercent,
                                  double daysWorked, int items, int points, int projectsTouched) {
        String name = userRepository.findById(employee.getUserId()).map(User::fullName).orElse("Employee");
        return new PersonLine(employee.getId().toString(), name, cost, billablePercent,
                Math.round(daysWorked * 10) / 10.0, items, points, projectsTouched);
    }

    // ---- inputs ----

    /** Current annual pay per employee, from one pass over the tenant's records (newest first). */
    private Map<UUID, BigDecimal> currentPayByEmployee(UUID companyId) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        for (CompensationRecord r : compensationRepository
                .findByCompanyIdOrderByEffectiveDateDescCreatedAtDesc(companyId)) {
            out.putIfAbsent(r.getEmployeeId(), r.getAnnualAmount());   // first seen is the newest
        }
        return out;
    }

    /**
     * Days this person actually worked. Attendance is the truth when it exists; a company that does
     * not record it still pays salaries, so we fall back to the period's weekdays rather than
     * reporting everyone as costing nothing.
     */
    private double daysWorked(UUID employeeId, LocalDate from, LocalDate to, long weekdays) {
        List<AttendanceRecord> records =
                attendanceRepository.findByEmployeeIdAndDateBetweenOrderByDateAsc(employeeId, from, to);
        if (records.isEmpty()) {
            return weekdays;
        }
        double days = 0;
        for (AttendanceRecord r : records) {
            AttendanceStatus status = r.getStatus();
            if (status == null) continue;
            if (status == AttendanceStatus.HALF_DAY) days += 0.5;
            else if (status.isWorking()) days += 1;
        }
        return days;
    }

    private static long weekdaysBetween(LocalDate from, LocalDate to) {
        long count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }
}
