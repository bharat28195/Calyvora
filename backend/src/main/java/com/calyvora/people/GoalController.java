package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.CreateGoalRequest;
import com.calyvora.people.dto.GoalResponse;
import com.calyvora.people.dto.UpdateGoalRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Employee goals (feedback C8). Viewable by anyone in the tenant; editable by an admin or the goal's
 * owner (self-service) — enforced in {@link GoalService}.
 */
@RestController
@RequestMapping("/api/v1/people/employees/{employeeId}/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public List<GoalResponse> list(@PathVariable UUID employeeId) {
        return goalService.list(employeeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@PathVariable UUID employeeId, @Valid @RequestBody CreateGoalRequest request,
                               @CurrentUser AuthPrincipal principal) {
        return goalService.create(employeeId, request, principal);
    }

    @PatchMapping("/{goalId}")
    public GoalResponse update(@PathVariable UUID employeeId, @PathVariable UUID goalId,
                               @Valid @RequestBody UpdateGoalRequest request, @CurrentUser AuthPrincipal principal) {
        return goalService.update(goalId, request, principal);
    }

    @DeleteMapping("/{goalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID employeeId, @PathVariable UUID goalId,
                       @CurrentUser AuthPrincipal principal) {
        goalService.delete(goalId, principal);
    }
}
