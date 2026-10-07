package com.calyvora.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollMonthRepository extends JpaRepository<PayrollMonth, UUID> {

    Optional<PayrollMonth> findByCompanyIdAndMonth(UUID companyId, String month);

    boolean existsByCompanyIdAndMonth(UUID companyId, String month);

    List<PayrollMonth> findByCompanyIdOrderByMonthDesc(UUID companyId);
}
