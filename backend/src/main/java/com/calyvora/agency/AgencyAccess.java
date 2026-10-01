package com.calyvora.agency;

import com.calyvora.common.security.TenantContext;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Second lock on the agency console, used from {@code @PreAuthorize} alongside the role check — the
 * same belt-and-braces the platform console uses, for the same reason.
 *
 * <p>Guarding on the {@code AGENCY_OWNER} role alone would make the console only as safe as the rule
 * that hands that role out. Requiring membership of a company explicitly flagged as an agency means a
 * stray {@code AGENCY_OWNER} row, however it appears, still reaches nothing.
 *
 * <p>Note this only opens the door. Which companies are behind it is a separate question, answered by
 * {@link AgencyService} filtering on {@code agency_id} for every single read.
 */
@Component("agencyAccess")
public class AgencyAccess {

    private final CompanyRepository companyRepository;

    public AgencyAccess(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /** @return true only for a caller belonging to an agency workspace. */
    public boolean granted() {
        UUID companyId = TenantContext.getCompanyIdOrNull();
        if (companyId == null) {
            return false;
        }
        return companyRepository.findById(companyId).map(Company::isAgency).orElse(false);
    }
}
