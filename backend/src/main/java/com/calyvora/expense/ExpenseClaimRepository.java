package com.calyvora.expense;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> {

    List<ExpenseClaim> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<ExpenseClaim> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    /**
     * One page of the company's claims, newest first, from a cursor.
     *
     * <p>Sorted and compared on {@code (createdAt, id)} together, because claims are filed in
     * batches — a trip settled for a whole team at once — and a boundary inside such a batch would
     * drop and repeat rows without the tiebreaker.
     */
    @Query("select c from ExpenseClaim c where c.companyId = :companyId "
            + "and c.status in :statuses "
            + "and (c.createdAt < :ts or (c.createdAt = :ts and c.id < :id)) "
            + "order by c.createdAt desc, c.id desc")
    List<ExpenseClaim> pageForCompany(UUID companyId, Collection<ExpenseStatus> statuses,
                                      Instant ts, UUID id, Pageable limit);

    /** The same page for one person's own claims. */
    @Query("select c from ExpenseClaim c where c.companyId = :companyId and c.employeeId = :employeeId "
            + "and c.status in :statuses "
            + "and (c.createdAt < :ts or (c.createdAt = :ts and c.id < :id)) "
            + "order by c.createdAt desc, c.id desc")
    List<ExpenseClaim> pageForEmployee(UUID companyId, UUID employeeId, Collection<ExpenseStatus> statuses,
                                       Instant ts, UUID id, Pageable limit);

    /**
     * The money totals, summed by the database over every matching row.
     *
     * <p>These have to be aggregates now that the claims are paged. Adding up the page instead would
     * quietly turn "outstanding across the company" into "outstanding among the fifty most recent
     * claims" — a wrong number on a finance screen, which is worse than a slow one, and wrong in a
     * way nobody would notice until they reconciled against a bank statement.
     *
     * <p>{@code coalesce} because SUM over no rows is null, and a null total renders as a blank box
     * rather than zero.
     */
    @Query("select coalesce(sum(c.amount), 0) from ExpenseClaim c "
            + "where c.companyId = :companyId and c.status = :status")
    BigDecimal sumForCompany(UUID companyId, ExpenseStatus status);

    @Query("select coalesce(sum(c.amount), 0) from ExpenseClaim c "
            + "where c.companyId = :companyId and c.employeeId = :employeeId and c.status = :status")
    BigDecimal sumForEmployee(UUID companyId, UUID employeeId, ExpenseStatus status);

    @Query("select coalesce(sum(c.amount), 0) from ExpenseClaim c "
            + "where c.companyId = :companyId and c.status = :status "
            + "and c.reimbursedAt >= :from and c.reimbursedAt < :to")
    BigDecimal sumReimbursedForCompany(UUID companyId, ExpenseStatus status, Instant from, Instant to);

    @Query("select coalesce(sum(c.amount), 0) from ExpenseClaim c "
            + "where c.companyId = :companyId and c.employeeId = :employeeId and c.status = :status "
            + "and c.reimbursedAt >= :from and c.reimbursedAt < :to")
    BigDecimal sumReimbursedForEmployee(UUID companyId, UUID employeeId, ExpenseStatus status,
                                        Instant from, Instant to);

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
