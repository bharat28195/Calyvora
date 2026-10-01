package com.calyvora.people;

import com.calyvora.people.dto.LeavePolicyPayload;
import com.calyvora.people.dto.LeavePolicyResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A company's leave rules.
 *
 * <p>Readable by anyone in the company — an employee is entitled to know how their own leave is
 * calculated, and the balance screen shows the accrual behind each number. Editable only by HR and
 * admins, because changing an entitlement changes what everybody already believes they have.
 */
@RestController
@RequestMapping("/api/v1/people/leave-policies")
public class LeavePolicyController {

    private final LeavePolicyService service;

    public LeavePolicyController(LeavePolicyService service) {
        this.service = service;
    }

    @GetMapping
    public List<LeavePolicyResponse> list() {
        return service.list();
    }

    @PatchMapping("/{type}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
    public LeavePolicyResponse update(@PathVariable String type,
                                      @Valid @RequestBody LeavePolicyPayload payload) {
        return service.update(LeavePolicyService.parseType(type), payload);
    }
}
