package com.calyvora.work;

import com.calyvora.common.security.TenantContext;
import com.calyvora.work.dto.WorkItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The open Work items assigned to an employee — powers "what he/she is working on" on the People
 * profile (feedback C4). Lives in {@code work} to keep the People → Work dependency out of People.
 */
@Service
public class AssignedWorkService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public AssignedWorkService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<WorkItemResponse> forEmployee(UUID employeeId) {
        UUID companyId = TenantContext.getCompanyId();
        Map<UUID, Project> projects = projectRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));

        LocalDate today = LocalDate.now();
        List<WorkItemResponse> out = new ArrayList<>();
        for (Task t : taskRepository.findByAssigneeIdAndStatusNotOrderByDueDateAscCreatedAtAsc(employeeId, TaskStatus.DONE)) {
            if (!companyId.equals(t.getCompanyId())) {
                continue;   // belt-and-suspenders alongside RLS
            }
            Project p = projects.get(t.getProjectId());
            String ref = p == null ? "#" + t.getNumber() : p.getKey() + "-" + t.getNumber();
            boolean overdue = t.getDueDate() != null && t.getDueDate().isBefore(today);
            out.add(new WorkItemResponse(ref, t.getTitle(), t.getStatus().name(), t.getPriority().name(),
                    t.getProjectId().toString(), p == null ? null : p.getName(),
                    t.getDueDate() == null ? null : t.getDueDate().toString(), overdue));
        }
        return out;
    }
}
