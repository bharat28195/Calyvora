package com.calyvora.company;

import com.calyvora.company.dto.CompanyResponse;
import com.calyvora.company.dto.CompanySettingsResponse;
import com.calyvora.company.dto.MemberResponse;
import com.calyvora.company.dto.UpdateSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/company")
public class CompanyController {

    private final CompanyService companyService;
    private final com.calyvora.shift.ShiftService shiftService;

    public CompanyController(CompanyService companyService, com.calyvora.shift.ShiftService shiftService) {
        this.companyService = companyService;
        this.shiftService = shiftService;
    }

    /** Any authenticated member can view their company. */
    @GetMapping
    public CompanyResponse getCompany() {
        return companyService.getCompany();
    }

    @GetMapping("/settings")
    public CompanySettingsResponse getSettings() {
        return companyService.getSettings();
    }

    @PatchMapping("/settings")
    @PreAuthorize("@perm.has('COMPANY_SETTINGS')")
    public CompanySettingsResponse updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return companyService.updateSettings(request);
    }

    /**
     * The working day and whether attendance is tracked (2026-11-14). The same settings Shifts edits,
     * here so they work for a company without the Shifts module: the rules apply to everyone, rostered
     * or not, so they cannot depend on buying the roster.
     */
    @GetMapping("/work-day")
    @PreAuthorize("@perm.has('COMPANY_SETTINGS') or @perm.has('SHIFTS_MANAGE')")
    public com.calyvora.shift.dto.WorkDayPayload workDay() {
        return shiftService.workDay();
    }

    @org.springframework.web.bind.annotation.PutMapping("/work-day")
    @PreAuthorize("@perm.has('COMPANY_SETTINGS') or @perm.has('SHIFTS_MANAGE')")
    public com.calyvora.shift.dto.WorkDayPayload setWorkDay(
            @Valid @RequestBody com.calyvora.shift.dto.WorkDayPayload req) {
        return shiftService.setWorkDay(req);
    }

    @GetMapping("/members")
    @PreAuthorize("@perm.has('MEMBERS_MANAGE')")
    public List<MemberResponse> listMembers() {
        return companyService.listMembers();
    }
}
