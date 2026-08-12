package com.calyvora.work;

import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanyZone;
import com.calyvora.work.dto.ContributionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What an employee delivered between two dates (PD-17, D1) — the factual half of an evidence-based
 * performance review. Sibling of {@link AssignedWorkService}: that one answers "what are they working
 * on now", this one answers "what did they finish during this period".
 *
 * <p>Lives in {@code work} so the Performance module never touches Work's entities — it receives a
 * rendered DTO, exactly as People does for the profile's work list.
 */
@Service
public class ContributionService {

    private final TaskRepository taskRepository;
    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final CompanyZone companyZone;

    public ContributionService(TaskRepository taskRepository, TicketRepository ticketRepository,
                               ProjectRepository projectRepository, SprintRepository sprintRepository,
                               CompanyZone companyZone) {
        this.taskRepository = taskRepository;
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.sprintRepository = sprintRepository;
        this.companyZone = companyZone;
    }

    /**
     * @param from inclusive first day of the period
     * @param to   inclusive last day — the whole day counts, so the window closes at its final instant
     */
    @Transactional(readOnly = true)
    public ContributionResponse forEmployee(UUID employeeId, LocalDate from, LocalDate to) {
        if (employeeId == null || from == null || to == null || to.isBefore(from)) {
            return ContributionResponse.empty();
        }
        UUID companyId = TenantContext.getCompanyId();

        // A review period is given in dates; completion is stamped as an instant. Convert on the
        // *company's* clock, not the server's: for an IST tenant, resolving in UTC would push the
        // first five and a half hours of the opening day outside its own review, and pull the same
        // slice of the day after the close back into it. Same lesson attendance learned (PD-16).
        // The whole of the last day counts, so the window closes at the final instant.
        Instant start = companyZone.startOfDay(from);
        Instant end = companyZone.endOfDayExclusive(to);

        Map<UUID, Project> projects = projectRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));

        List<Task> delivered = taskRepository
                .findByCompanyIdAndAssigneeIdAndCompletedAtBetweenOrderByCompletedAtDesc(
                        companyId, employeeId, start, end);
        List<Ticket> resolved = ticketRepository
                .findByCompanyIdAndAssigneeIdAndResolvedAtBetweenOrderByResolvedAtDesc(
                        companyId, employeeId, start, end);

        List<ContributionResponse.Item> taskItems = new ArrayList<>();
        int points = 0;
        for (Task t : delivered) {
            Project p = projects.get(t.getProjectId());
            taskItems.add(new ContributionResponse.Item(
                    ref(p, t.getNumber()), t.getTitle(), t.getProjectId().toString(),
                    p == null ? null : p.getName(), "/work/" + t.getProjectId(),
                    day(t.getCompletedAt()), t.getStoryPoints()));
            if (t.getStoryPoints() != null) {
                points += t.getStoryPoints();
            }
        }

        List<ContributionResponse.Item> ticketItems = new ArrayList<>();
        for (Ticket t : resolved) {
            Project p = projects.get(t.getProjectId());
            ticketItems.add(new ContributionResponse.Item(
                    ref(p, t.getNumber()), t.getSubject(), t.getProjectId().toString(),
                    p == null ? null : p.getName(), "/work/" + t.getProjectId(),
                    day(t.getResolvedAt()), null));
        }

        return new ContributionResponse(taskItems, ticketItems, sprintsFrom(delivered, projects),
                taskItems.size(), ticketItems.size(), points);
    }

    /**
     * The sprints this person delivered into, derived from the delivered tasks themselves. Each
     * carries *their* share — items and points they closed — not the sprint's totals, because a
     * review is about this person's contribution, not the team's.
     */
    private List<ContributionResponse.SprintItem> sprintsFrom(List<Task> delivered, Map<UUID, Project> projects) {
        Map<UUID, int[]> tally = new LinkedHashMap<>();   // sprintId -> [items, points]
        for (Task t : delivered) {
            if (t.getSprintId() == null) continue;
            int[] counts = tally.computeIfAbsent(t.getSprintId(), k -> new int[2]);
            counts[0]++;
            if (t.getStoryPoints() != null) {
                counts[1] += t.getStoryPoints();
            }
        }
        List<ContributionResponse.SprintItem> out = new ArrayList<>();
        for (Map.Entry<UUID, int[]> e : tally.entrySet()) {
            sprintRepository.findById(e.getKey()).ifPresent(s -> {
                Project p = projects.get(s.getProjectId());
                out.add(new ContributionResponse.SprintItem(
                        s.getId().toString(), s.getName(), p == null ? null : p.getName(),
                        s.getStartDate() == null ? null : s.getStartDate().toString(),
                        s.getEndDate() == null ? null : s.getEndDate().toString(),
                        s.getStatus().name(), e.getValue()[0], e.getValue()[1]));
            });
        }
        return out;
    }

    private static String ref(Project project, int number) {
        return project == null ? "#" + number : project.getKey() + "-" + number;
    }

    /** The calendar date a completion falls on, as the company reads its own calendar. */
    private String day(Instant instant) {
        return instant == null ? null : instant.atZone(companyZone.forCurrentTenant()).toLocalDate().toString();
    }
}
