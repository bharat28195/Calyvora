package com.calyvora.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayslipSnapshotRepository extends JpaRepository<PayslipSnapshot, UUID> {

    List<PayslipSnapshot> findByCompanyIdAndMonth(UUID companyId, String month);

    Optional<PayslipSnapshot> findByCompanyIdAndMonthAndEmployeeId(UUID companyId, String month, UUID employeeId);

    /** Every finalised month in a range — months are YYYY-MM, so string order is month order. */
    List<PayslipSnapshot> findByCompanyIdAndMonthBetween(UUID companyId, String from, String to);

    List<PayslipSnapshot> findByCompanyIdAndMonthIn(UUID companyId, Collection<String> months);

    @Modifying
    void deleteByCompanyIdAndMonth(UUID companyId, String month);
}
