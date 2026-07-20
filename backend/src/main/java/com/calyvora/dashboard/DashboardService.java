package com.calyvora.dashboard;

import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanyRepository;
import com.calyvora.dashboard.dto.DashboardSummaryResponse;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.invitation.InvitationRepository;
import com.calyvora.invitation.InvitationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** At-a-glance company summary for the dashboard (F7). Tenant-scoped. */
@Service
public class DashboardService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;

    public DashboardService(CompanyRepository companyRepository,
                            UserRepository userRepository,
                            InvitationRepository invitationRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(String role) {
        UUID companyId = TenantContext.getCompanyId();
        String companyName = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"))
                .getName();
        long members = userRepository.countByCompanyIdAndStatus(companyId, UserStatus.ACTIVE);
        long pendingInvites = invitationRepository.countByCompanyIdAndStatus(companyId, InvitationStatus.PENDING);
        return new DashboardSummaryResponse(companyName, members, pendingInvites, role);
    }
}
