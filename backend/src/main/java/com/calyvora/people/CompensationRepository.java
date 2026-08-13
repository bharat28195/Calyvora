package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompensationRepository extends JpaRepository<CompensationRecord, UUID> {

    /** Newest first (current pay is the first element). */
    List<CompensationRecord> findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(UUID employeeId);

    /**
     * Every pay record in the tenant, newest first. Builds the whole company's cost picture in one
     * query instead of one per head (D2 profitability).
     */
    List<CompensationRecord> findByCompanyIdOrderByEffectiveDateDescCreatedAtDesc(UUID companyId);
}
