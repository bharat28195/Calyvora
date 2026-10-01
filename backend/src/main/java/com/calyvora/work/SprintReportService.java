package com.calyvora.work;

import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.work.dto.SprintReportResponse;
import com.calyvora.work.dto.SprintReportResponse.BurndownPoint;
import com.calyvora.work.dto.SprintReportResponse.MemberLoad;
import com.calyvora.work.dto.VelocityResponse;
import com.calyvora.work.dto.VelocityResponse.SprintVelocity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint reporting (founder request: the sprint features every team expects) — burndown, velocity,
 * capacity and per-person load.
 *
 * <p>The burndown reads recorded daily snapshots rather than recomputing from current state, because
 * today's board can't tell you what remained last Tuesday. {@link #snapshotToday} writes one row per
 * sprint per day, and every board mutation triggers it, so the line is drawn from what actually
 * happened.
 */
@Service
public class SprintReportService {

    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final SprintSnapshotRepository snapshotRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public SprintReportService(SprintRepository sprintRepository, TaskRepository taskRepository,
                               SprintSnapshotRepository snapshotRepository,
                               EmployeeRepository employeeRepository, UserRepository userRepository) {
        this.sprintRepository = sprintRepository;
        this.taskRepository = taskRepository;
        this.snapshotRepository = snapshotRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    // ---- the sprint report ----

    @Transactional
    public SprintReportResponse report(UUID sprintId) {
        UUID companyId = TenantContext.getCompanyId();
        Sprint sprint = sprintRepository.findByIdAndCompanyId(sprintId, companyId)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));
        List<Task> tasks = taskRepository.findBySprintId(sprintId);

        int committed = 0;
        int completed = 0;
        int unestimated = 0;
        int done = 0;
        for (Task t : tasks) {
            int points = t.getStoryPoints() == null ? 0 : t.getStoryPoints();
            if (t.getStoryPoints() == null) unestimated++;
            committed += points;
            if (t.getStatus() == TaskStatus.DONE) {
                completed += points;
                done++;
            }
        }

        // Keep today's snapshot current so the chart always ends at "now".
        snapshotToday(sprint, tasks);

        return new SprintReportResponse(
                sprint.getId().toString(), sprint.getName(), sprint.getGoal(), sprint.getStatus().name(),
                dateOrNull(sprint.getStartDate()), dateOrNull(sprint.getEndDate()),
                sprint.getCapacityPoints(), committed, completed, committed - completed,
                tasks.size(), done, unestimated,
                daysBetween(sprint), elapsedDays(sprint),
                burndown(sprint, committed),
                byAssignee(companyId, tasks));
    }

    /**
     * Records (or refreshes) today's remaining work for a sprint. Called after board changes and
     * whenever a report is read, so the burndown reflects reality without a scheduled job.
     */
    @Transactional
    public void snapshotToday(Sprint sprint, List<Task> tasks) {
        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            return;   // only an in-flight sprint has a meaningful daily figure
        }
        int remainingPoints = 0;
        int completedPoints = 0;
        int remainingTasks = 0;
        for (Task t : tasks) {
            int points = t.getStoryPoints() == null ? 0 : t.getStoryPoints();
            if (t.getStatus() == TaskStatus.DONE) {
                completedPoints += points;
            } else {
                remainingPoints += points;
                remainingTasks++;
            }
        }
        LocalDate today = LocalDate.now();
        final int rp = remainingPoints;
        final int cp = completedPoints;
        final int rt = remainingTasks;
        snapshotRepository.findBySprintIdAndDate(sprint.getId(), today)
                .ifPresentOrElse(
                        s -> s.update(rp, cp, rt),
                        () -> snapshotRepository.save(new SprintSnapshot(UUID.randomUUID(),
                                sprint.getCompanyId(), sprint.getId(), today, rp, cp, rt)));
    }

    /** Convenience for callers that only have the sprint id (e.g. after a task moves). */
    @Transactional
    public void snapshotSprint(UUID sprintId) {
        UUID companyId = TenantContext.getCompanyId();
        sprintRepository.findByIdAndCompanyId(sprintId, companyId)
                .ifPresent(s -> snapshotToday(s, taskRepository.findBySprintId(sprintId)));
    }

    // ---- velocity ----

    @Transactional(readOnly = true)
    public VelocityResponse velocity(UUID projectId) {
        UUID companyId = TenantContext.getCompanyId();
        List<SprintVelocity> rows = new ArrayList<>();
        double total = 0;

        List<Sprint> sprints = sprintRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(s -> s.getCompanyId().equals(companyId))
                .filter(s -> s.getStatus() == SprintStatus.COMPLETED)
                .toList();

        for (Sprint s : sprints) {
            int committed = 0;
            int completed = 0;
            for (Task t : taskRepository.findBySprintId(s.getId())) {
                int points = t.getStoryPoints() == null ? 0 : t.getStoryPoints();
                committed += points;
                if (t.getStatus() == TaskStatus.DONE) completed += points;
            }
            rows.add(new SprintVelocity(s.getId().toString(), s.getName(), dateOrNull(s.getEndDate()),
                    committed, completed));
            total += completed;
        }

        // Oldest first reads better on a chart.
        List<SprintVelocity> chronological = new ArrayList<>(rows);
        java.util.Collections.reverse(chronological);
        double average = rows.isEmpty() ? 0 : Math.round((total / rows.size()) * 10) / 10.0;
        return new VelocityResponse(chronological, average, (int) Math.round(average));
    }

    // ---- helpers ----

    /**
     * The chart: recorded snapshots for days that have passed, plus the ideal straight line across
     * the whole sprint. Days with no snapshot (before the sprint started, or after today) carry a
     * null remaining value so the UI can stop the actual line where the data stops.
     */
    private List<BurndownPoint> burndown(Sprint sprint, int committed) {
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            return List.of();
        }
        Map<LocalDate, Integer> recorded = new HashMap<>();
        for (SprintSnapshot s : snapshotRepository.findBySprintIdOrderByDateAsc(sprint.getId())) {
            recorded.put(s.getDate(), s.getRemainingPoints());
        }

        long span = Math.max(1, ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()));
        LocalDate today = LocalDate.now();
        List<BurndownPoint> points = new ArrayList<>();
        for (LocalDate d = sprint.getStartDate(); !d.isAfter(sprint.getEndDate()); d = d.plusDays(1)) {
            long elapsed = ChronoUnit.DAYS.between(sprint.getStartDate(), d);
            double ideal = Math.round((committed - (committed * (double) elapsed / span)) * 10) / 10.0;
            points.add(new BurndownPoint(d.toString(), recorded.get(d), ideal, d.isAfter(today)));
        }
        return points;
    }

    /** Who is carrying what, so an overloaded person is visible at standup rather than at the retro. */
    private List<MemberLoad> byAssignee(UUID companyId, List<Task> tasks) {
        Map<UUID, int[]> totals = new HashMap<>();   // employeeId -> [points, tasks, donePoints]
        for (Task t : tasks) {
            if (t.getAssigneeId() == null) {
                continue;
            }
            int points = t.getStoryPoints() == null ? 0 : t.getStoryPoints();
            int[] row = totals.computeIfAbsent(t.getAssigneeId(), k -> new int[3]);
            row[0] += points;
            row[1] += 1;
            if (t.getStatus() == TaskStatus.DONE) row[2] += points;
        }

        List<MemberLoad> loads = new ArrayList<>();
        totals.forEach((employeeId, row) -> loads.add(new MemberLoad(employeeId.toString(),
                nameOf(companyId, employeeId), row[0], row[1], row[2])));
        loads.sort((a, b) -> Integer.compare(b.points(), a.points()));
        return loads;
    }

    private String nameOf(UUID companyId, UUID employeeId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .flatMap(e -> userRepository.findByIdAndCompanyId(e.getUserId(), companyId))
                .map(User::fullName)
                .orElse("Unassigned");
    }

    private static int daysBetween(Sprint sprint) {
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()) + 1;
    }

    private static int elapsedDays(Sprint sprint) {
        if (sprint.getStartDate() == null) {
            return 0;
        }
        LocalDate end = sprint.getEndDate() == null ? LocalDate.now() : sprint.getEndDate();
        LocalDate today = LocalDate.now().isAfter(end) ? end : LocalDate.now();
        return (int) Math.max(0, ChronoUnit.DAYS.between(sprint.getStartDate(), today)) + 1;
    }

    private static String dateOrNull(LocalDate d) {
        return d == null ? null : d.toString();
    }
}
