package com.calyvora.payroll;

import com.calyvora.payroll.dto.StatutorySettingsPayload;
import com.calyvora.payroll.dto.StatutorySettingsResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ESI / professional-tax settings and registration numbers — payroll managers only, like PF. */
@RestController
@RequestMapping("/api/v1/payroll/statutory-settings")
@PreAuthorize("@perm.has('PAYROLL_MANAGE')")
public class StatutorySettingsController {

    private final StatutorySettingsService service;

    public StatutorySettingsController(StatutorySettingsService service) {
        this.service = service;
    }

    @GetMapping
    public StatutorySettingsResponse get() {
        return service.current();
    }

    @PatchMapping
    public StatutorySettingsResponse update(@Valid @RequestBody StatutorySettingsPayload payload) {
        return service.update(payload);
    }
}
