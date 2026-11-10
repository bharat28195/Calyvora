package com.calyvora.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaxHousePropertyRepository extends JpaRepository<TaxHouseProperty, UUID> {
    List<TaxHouseProperty> findByDeclarationId(UUID declarationId);
    List<TaxHouseProperty> findByDeclarationIdIn(Collection<UUID> declarationIds);
}
