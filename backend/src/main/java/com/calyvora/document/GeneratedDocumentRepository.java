package com.calyvora.document;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {

    List<GeneratedDocument> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<GeneratedDocument> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    Optional<GeneratedDocument> findByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * One page of issued documents, newest first, from a cursor.
     *
     * <p>Letters are generated in batches — an appraisal round issues one per employee in a loop — so
     * a creation instant is not unique here and the id tiebreaker is what keeps a page boundary
     * falling inside such a batch from dropping and repeating rows.
     */
    @Query("""
            select d from GeneratedDocument d
            where d.companyId = :companyId
              and (d.createdAt < :ts or (d.createdAt = :ts and d.id < :id))
            order by d.createdAt desc, d.id desc
            """)
    List<GeneratedDocument> pageForCompany(@Param("companyId") UUID companyId, @Param("ts") Instant ts,
                                           @Param("id") UUID id, Pageable limit);

    /** The same page for one person's file. */
    @Query("""
            select d from GeneratedDocument d
            where d.companyId = :companyId and d.employeeId = :employeeId
              and (d.createdAt < :ts or (d.createdAt = :ts and d.id < :id))
            order by d.createdAt desc, d.id desc
            """)
    List<GeneratedDocument> pageForEmployee(@Param("companyId") UUID companyId,
                                            @Param("employeeId") UUID employeeId,
                                            @Param("ts") Instant ts, @Param("id") UUID id, Pageable limit);

    /** Tenant-scoped title search (for global search). */
    @Query("""
            select d from GeneratedDocument d
            where d.companyId = :companyId
              and lower(d.title) like lower(concat('%', :q, '%'))
            order by d.createdAt desc
            """)
    List<GeneratedDocument> search(@Param("companyId") UUID companyId, @Param("q") String q, Pageable pageable);
}
