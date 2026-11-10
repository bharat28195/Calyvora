package com.calyvora.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LetterSignatoryRepository extends JpaRepository<LetterSignatory, UUID> {

    List<LetterSignatory> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    Optional<LetterSignatory> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<LetterSignatory> findFirstByCompanyIdAndActiveTrueAndIsDefaultTrue(UUID companyId);
}
