package com.calyvora.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaxDeclarationItemRepository extends JpaRepository<TaxDeclarationItem, UUID> {

    List<TaxDeclarationItem> findByDeclarationId(UUID declarationId);

    /** Every item for a set of declarations, so a list of people costs one query rather than N. */
    List<TaxDeclarationItem> findByDeclarationIdIn(Collection<UUID> declarationIds);

    void deleteByDeclarationId(UUID declarationId);
}
