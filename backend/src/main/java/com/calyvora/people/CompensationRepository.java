package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompensationRepository extends JpaRepository<CompensationRecord, UUID> {

    /** Newest first (current pay is the first element). */
    List<CompensationRecord> findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(UUID employeeId);

    /**
     * Every salary row in the company, newest first.
     *
     * <p>A payroll run needs to know who has a salary at all before it does any work for them. Asking
     * per employee is one round trip each — a thousand queries to discover that a thousand people are
     * not on payroll, which is slower than the work it was meant to avoid.
     */
    List<CompensationRecord> findByCompanyIdOrderByEffectiveDateDescCreatedAtDesc(UUID companyId);
}
