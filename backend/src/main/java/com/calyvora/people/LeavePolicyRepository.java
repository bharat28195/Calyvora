package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, UUID> {

    List<LeavePolicy> findByCompanyIdOrderByTypeAsc(UUID companyId);

    Optional<LeavePolicy> findByCompanyIdAndType(UUID companyId, LeaveType type);
}
