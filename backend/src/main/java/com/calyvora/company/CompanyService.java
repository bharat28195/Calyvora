package com.calyvora.company;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.company.dto.CompanyResponse;
import com.calyvora.company.dto.CompanySettingsResponse;
import com.calyvora.company.dto.MemberResponse;
import com.calyvora.company.dto.UpdateSettingsRequest;
import com.calyvora.identity.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Company profile, settings, and member listing — all strictly tenant-scoped via
 * {@link TenantContext} (SD-2). Role gates (OWNER/ADMIN) are enforced at the controller.
 */
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository settingsRepository;
    private final UserRepository userRepository;

    public CompanyService(CompanyRepository companyRepository,
                          CompanySettingsRepository settingsRepository,
                          UserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany() {
        UUID companyId = TenantContext.getCompanyId();
        return companyRepository.findById(companyId)
                .map(CompanyResponse::of)
                .orElseThrow(() -> new NotFoundException("Company not found"));
    }

    @Transactional(readOnly = true)
    public CompanySettingsResponse getSettings() {
        UUID companyId = TenantContext.getCompanyId();
        return settingsRepository.findById(companyId)
                .map(CompanySettingsResponse::of)
                .orElseThrow(() -> new NotFoundException("Company settings not found"));
    }

    @Transactional
    public CompanySettingsResponse updateSettings(UpdateSettingsRequest request) {
        UUID companyId = TenantContext.getCompanyId();
        CompanySettings settings = settingsRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company settings not found"));

        if (!isValidZone(request.timezone())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unknown timezone");
        }
        settings.setTimezone(request.timezone());
        settings.setLocale(request.locale());
        settings.setCurrency(request.currency());
        settings.setLegalName(blankToNull(request.legalName()));
        settings.setAddress(blankToNull(request.address()));
        settings.setLogoUrl(request.logoUrl() == null || request.logoUrl().isBlank()
                ? null : request.logoUrl());
        return CompanySettingsResponse.of(settings);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers() {
        UUID companyId = TenantContext.getCompanyId();
        return userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .map(MemberResponse::of)
                .toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean isValidZone(String zone) {
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }
}
