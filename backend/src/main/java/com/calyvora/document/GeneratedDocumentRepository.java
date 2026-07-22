package com.calyvora.document;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {

    List<GeneratedDocument> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<GeneratedDocument> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    Optional<GeneratedDocument> findByIdAndCompanyId(UUID id, UUID companyId);

    /** Tenant-scoped title search (for global search). */
    @Query("""
            select d from GeneratedDocument d
            where d.companyId = :companyId
              and lower(d.title) like lower(concat('%', :q, '%'))
            order by d.createdAt desc
            """)
    List<GeneratedDocument> search(@Param("companyId") UUID companyId, @Param("q") String q, Pageable pageable);
}
