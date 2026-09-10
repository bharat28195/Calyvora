package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeFinanceRepository extends JpaRepository<EmployeeFinance, UUID> {

    Optional<EmployeeFinance> findByEmployeeIdAndCompanyId(UUID employeeId, UUID companyId);

    /** Every finance row in the company — for a payroll run, which needs all of them at once. */
    List<EmployeeFinance> findByCompanyId(UUID companyId);
}
