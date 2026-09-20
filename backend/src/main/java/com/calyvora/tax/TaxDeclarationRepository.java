package com.calyvora.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxDeclarationRepository extends JpaRepository<TaxDeclaration, UUID> {

    Optional<TaxDeclaration> findByCompanyIdAndEmployeeIdAndFinancialYear(
            UUID companyId, UUID employeeId, String financialYear);

    List<TaxDeclaration> findByCompanyIdAndFinancialYear(UUID companyId, String financialYear);

    /** One page's worth by employee, for the HR list — never a query per row. */
    List<TaxDeclaration> findByCompanyIdAndFinancialYearAndEmployeeIdIn(
            UUID companyId, String financialYear, java.util.Collection<UUID> employeeIds);
}
