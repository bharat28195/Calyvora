package com.calyvora.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TdsOpeningBalanceRepository extends JpaRepository<TdsOpeningBalance, UUID> {

    List<TdsOpeningBalance> findByCompanyIdAndFinancialYear(UUID companyId, String financialYear);

    Optional<TdsOpeningBalance> findByCompanyIdAndEmployeeIdAndFinancialYear(UUID companyId, UUID employeeId,
                                                                            String financialYear);
}
