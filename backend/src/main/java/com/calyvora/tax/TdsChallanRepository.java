package com.calyvora.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TdsChallanRepository extends JpaRepository<TdsChallan, UUID> {

    List<TdsChallan> findByCompanyIdAndMonthIn(UUID companyId, Collection<String> months);

    Optional<TdsChallan> findByCompanyIdAndMonth(UUID companyId, String month);
}
