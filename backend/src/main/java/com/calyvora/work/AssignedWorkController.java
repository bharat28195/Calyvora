package com.calyvora.work;

import com.calyvora.work.dto.WorkItemResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * "What an employee is working on" (feedback C4) — the open Work items assigned to them, surfaced on
 * their People profile. Tenant-scoped; any authenticated member may view (tasks aren't sensitive).
 */
@RestController
@RequestMapping("/api/v1/people/employees/{employeeId}")
public class AssignedWorkController {

    private final AssignedWorkService assignedWorkService;

    public AssignedWorkController(AssignedWorkService assignedWorkService) {
        this.assignedWorkService = assignedWorkService;
    }

    @GetMapping("/work")
    public List<WorkItemResponse> work(@PathVariable UUID employeeId) {
        return assignedWorkService.forEmployee(employeeId);
    }
}
