package com.calyvora.payroll;

import com.calyvora.payroll.dto.PfSettingsPayload;
import com.calyvora.payroll.dto.PfSettingsResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A company's Provident Fund rates.
 *
 * <p>HR and admins only. These numbers decide what comes out of every salary in the company, so they
 * sit with payroll rather than with general settings, and a member cannot read them.
 */
@RestController
@RequestMapping("/api/v1/payroll/pf-settings")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','HR')")
public class PfSettingsController {

    private final PfSettingsService service;

    public PfSettingsController(PfSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public PfSettingsResponse get() {
        return service.current();
    }

    @PatchMapping
    public PfSettingsResponse update(@Valid @RequestBody PfSettingsPayload payload) {
        return service.update(payload);
    }
}
