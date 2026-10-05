package com.calyvora.dashboard;

import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanyRepository;
import com.calyvora.dashboard.dto.DashboardSummaryResponse;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.invitation.InvitationRepository;
import com.calyvora.invitation.InvitationStatus;
import com.calyvora.knowledge.PageRepository;
import com.calyvora.knowledge.SpaceRepository;
import com.calyvora.people.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * At-a-glance company summary for the dashboard (F7), reaching across all three apps. Tenant-scoped
 * via {@link TenantContext}; every count is a cheap aggregate query, not a full fetch.
 */
@Service
public class DashboardService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final DepartmentRepository departmentRepository;
    private final SpaceRepository spaceRepository;
    private final PageRepository pageRepository;

    public DashboardService(CompanyRepository companyRepository, UserRepository userRepository,
                            InvitationRepository invitationRepository, DepartmentRepository departmentRepository,
                            SpaceRepository spaceRepository, PageRepository pageRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.departmentRepository = departmentRepository;
        this.spaceRepository = spaceRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(String role) {
        UUID companyId = TenantContext.getCompanyId();
        String companyName = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"))
                .getName();

        long members = userRepository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE);
        long pendingInvites = invitationRepository.countByCompanyIdAndStatus(companyId, InvitationStatus.PENDING);
        long departments = departmentRepository.countByCompanyId(companyId);

        long spaces = spaceRepository.countByCompanyId(companyId);
        long pages = pageRepository.countByCompanyId(companyId);

        return new DashboardSummaryResponse(companyName, role, members, pendingInvites, departments,
                spaces, pages);
    }
}
