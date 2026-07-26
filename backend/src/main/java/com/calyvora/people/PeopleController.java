package com.calyvora.people;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.people.dto.EmployeeResponse;
import com.calyvora.people.dto.UpdateEmployeeRequest;
import com.calyvora.people.dto.UpdateMyProfileRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** People OS — employee directory & profiles (slice P1). Base {@code /api/v1/people}. */
@RestController
@RequestMapping("/api/v1/people")
public class PeopleController {

    private final EmployeeService employeeService;

    public PeopleController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /** Directory: any authenticated member can browse their company's people. */
    @GetMapping("/employees")
    public List<EmployeeResponse> directory() {
        return employeeService.directory();
    }

    /** Paged, searchable directory — the scalable path for large companies. */
    @GetMapping("/employees/page")
    public com.calyvora.common.dto.PageResponse<EmployeeResponse> directoryPage(
            @org.springframework.web.bind.annotation.RequestParam(name = "q", required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(name = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(name = "size", defaultValue = "25") int size) {
        return employeeService.directoryPage(q, page, size);
    }

    @GetMapping("/employees/{id}")
    public EmployeeResponse get(@PathVariable UUID id) {
        return employeeService.get(id);
    }

    /** My own profile (auto-provisioned if missing). */
    @GetMapping("/me")
    public EmployeeResponse me(@CurrentUser AuthPrincipal principal) {
        return employeeService.me(principal.userId());
    }

    /** Self-service: update my own contact fields. */
    @PatchMapping("/me")
    public EmployeeResponse updateMe(@CurrentUser AuthPrincipal principal,
                                     @Valid @RequestBody UpdateMyProfileRequest request) {
        return employeeService.updateMe(principal.userId(), request);
    }

    /** Admin: update any employee's HR profile. */
    @PatchMapping("/employees/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeService.update(id, request);
    }
}
