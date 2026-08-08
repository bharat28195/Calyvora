package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeFinanceRepository extends JpaRepository<EmployeeFinance, UUID> {

    Optional<EmployeeFinance> findByEmployeeIdAndCompanyId(UUID employeeId, UUID companyId);
}
