package com.calyvora.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {

    List<DocumentTemplate> findByCompanyIdOrderByNameAsc(UUID companyId);

    Optional<DocumentTemplate> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyId(UUID companyId);

    /**
     * The template to reach for when a letter is raised automatically. {@code builtIn} ascending puts
     * a company's own template ahead of the starter of the same kind — if they wrote their own
     * joining letter, that is the one a new hire should receive.
     */
    Optional<DocumentTemplate> findFirstByCompanyIdAndKindOrderByBuiltInAscNameAsc(UUID companyId, DocumentKind kind);
}
