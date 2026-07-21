package com.calyvora.dashboard;

import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanyRepository;
import com.calyvora.dashboard.dto.DashboardSummaryResponse;
import com.calyvora.dashboard.dto.DashboardSummaryResponse.ActiveSprint;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.invitation.InvitationRepository;
import com.calyvora.invitation.InvitationStatus;
import com.calyvora.knowledge.PageRepository;
import com.calyvora.knowledge.SpaceRepository;
import com.calyvora.people.DepartmentRepository;
import com.calyvora.work.ProjectRepository;
import com.calyvora.work.Sprint;
import com.calyvora.work.SprintRepository;
import com.calyvora.work.SprintStatus;
import com.calyvora.work.TaskRepository;
import com.calyvora.work.TaskStatus;
import com.calyvora.work.TicketRepository;
import com.calyvora.work.TicketStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * At-a-glance company summary for the dashboard (F7), reaching across all three apps. Tenant-scoped
 * via {@link TenantContext}; every count is a cheap aggregate query, not a full fetch.
 */
@Service
public class DashboardService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TicketRepository ticketRepository;
    private final SprintRepository sprintRepository;
    private final SpaceRepository spaceRepository;
    private final PageRepository pageRepository;

    public DashboardService(CompanyRepository companyRepository, UserRepository userRepository,
                            InvitationRepository invitationRepository, DepartmentRepository departmentRepository,
                            ProjectRepository projectRepository, TaskRepository taskRepository,
                            TicketRepository ticketRepository, SprintRepository sprintRepository,
                            SpaceRepository spaceRepository, PageRepository pageRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.departmentRepository = departmentRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.ticketRepository = ticketRepository;
        this.sprintRepository = sprintRepository;
        this.spaceRepository = spaceRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(String role) {
        UUID companyId = TenantContext.getCompanyId();
        String companyName = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"))
                .getName();

        long members = userRepository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE);
        long pendingInvites = invitationRepository.countByCompanyIdAndStatus(companyId, InvitationStatus.PENDING);
        long departments = departmentRepository.countByCompanyId(companyId);

        long projects = projectRepository.countByCompanyId(companyId);
        long openTasks = taskRepository.countByCompanyIdAndStatusNot(companyId, TaskStatus.DONE);
        long doneTasks = taskRepository.countByCompanyIdAndStatus(companyId, TaskStatus.DONE);
        long openTickets = ticketRepository.countByCompanyIdAndStatusIn(companyId,
                List.of(TicketStatus.OPEN, TicketStatus.PENDING));

        long spaces = spaceRepository.countByCompanyId(companyId);
        long pages = pageRepository.countByCompanyId(companyId);

        ActiveSprint activeSprint = activeSprint(companyId);

        return new DashboardSummaryResponse(companyName, role, members, pendingInvites, departments,
                projects, openTasks, doneTasks, openTickets, spaces, pages, activeSprint);
    }

    /** The first running sprint company-wide, with its done/total progress — or null if none is active. */
    private ActiveSprint activeSprint(UUID companyId) {
        List<Sprint> active = sprintRepository
                .findByCompanyIdAndStatusOrderByStartDateAsc(companyId, SprintStatus.ACTIVE);
        if (active.isEmpty()) {
            return null;
        }
        Sprint sprint = active.get(0);
        long total = taskRepository.countBySprintId(sprint.getId());
        long done = taskRepository.countBySprintIdAndStatus(sprint.getId(), TaskStatus.DONE);
        return new ActiveSprint(sprint.getName(), total, done);
    }
}
