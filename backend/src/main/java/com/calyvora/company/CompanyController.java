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

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
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
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public CompanySettingsResponse updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return companyService.updateSettings(request);
    }

    @GetMapping("/members")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<MemberResponse> listMembers() {
        return companyService.listMembers();
    }
}
