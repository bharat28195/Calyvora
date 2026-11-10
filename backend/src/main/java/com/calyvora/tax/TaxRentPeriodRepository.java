package com.calyvora.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaxRentPeriodRepository extends JpaRepository<TaxRentPeriod, UUID> {
    List<TaxRentPeriod> findByDeclarationIdOrderByFromMonthAsc(UUID declarationId);
    List<TaxRentPeriod> findByDeclarationIdIn(Collection<UUID> declarationIds);
}
