package com.calyvora.platform;

import com.calyvora.common.security.TenantContext;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Second lock on the platform console, used from {@code @PreAuthorize} alongside the role check.
 *
 * <p>The console reads every tenant's data, so guarding it on the {@code OWNER} role alone made the
 * whole platform only as safe as the rule that hands that role out — and for a while that rule was
 * "anyone who signs up" (fixed in V35). Requiring membership of the company explicitly marked as the
 * platform means a stray OWNER row, however it appears, still can't see another customer's data.
 */
@Component("platformAccess")
public class PlatformAccess {

    private final CompanyRepository companyRepository;

    public PlatformAccess(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /** @return true only for a caller belonging to the platform company. */
    public boolean granted() {
        UUID companyId = TenantContext.getCompanyIdOrNull();
        if (companyId == null) {
            return false;
        }
        return companyRepository.findById(companyId).map(Company::isPlatform).orElse(false);
    }
}
