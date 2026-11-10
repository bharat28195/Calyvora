package com.calyvora.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TdsQuarterReturnRepository extends JpaRepository<TdsQuarterReturn, UUID> {

    List<TdsQuarterReturn> findByCompanyIdAndQuarterStartingWith(UUID companyId, String financialYear);

    Optional<TdsQuarterReturn> findByCompanyIdAndQuarter(UUID companyId, String quarter);
}
