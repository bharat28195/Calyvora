package com.calyvora.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> {

    List<ExpenseClaim> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<ExpenseClaim> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<ExpenseClaim> findByIdAndCompanyId(UUID id, UUID companyId);

    /** For the assistant: claims waiting on someone. */
    long countByCompanyIdAndStatus(UUID companyId, ExpenseStatus status);
}
