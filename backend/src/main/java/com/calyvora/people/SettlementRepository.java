package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    Optional<Settlement> findByEmployeeIdAndCompanyId(UUID employeeId, UUID companyId);

    List<Settlement> findByCompanyIdOrderByUpdatedAtDesc(UUID companyId);
}
