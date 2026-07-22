package com.calyvora.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {

    List<DocumentTemplate> findByCompanyIdOrderByNameAsc(UUID companyId);

    Optional<DocumentTemplate> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyId(UUID companyId);
}
