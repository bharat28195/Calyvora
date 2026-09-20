package com.calyvora.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> {

    List<ExpenseClaim> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<ExpenseClaim> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /** One roster's claims — the team screens, which must not read the company's. */
    List<ExpenseClaim> findByCompanyIdAndEmployeeIdInOrderByCreatedAtDesc(
            UUID companyId, Collection<UUID> employeeIds);

    /** Only the claims still owed somebody an answer, for the roster in hand. */
    List<ExpenseClaim> findByCompanyIdAndEmployeeIdInAndStatusIn(
            UUID companyId, Collection<UUID> employeeIds, Collection<ExpenseStatus> statuses);

    Optional<ExpenseClaim> findByIdAndCompanyId(UUID id, UUID companyId);

    /** For the assistant: claims waiting on someone. */
    long countByCompanyIdAndStatus(UUID companyId, ExpenseStatus status);
}
