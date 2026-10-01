package com.calyvora.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PageRepository extends JpaRepository<Page, UUID> {

    List<Page> findBySpaceIdOrderBySortOrderAscCreatedAtAsc(UUID spaceId);

    Optional<Page> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Page> findByAuthorIdOrderByUpdatedAtDesc(UUID authorId);

    List<Page> findByParentId(UUID parentId);

    long countBySpaceId(UUID spaceId);

    long countByCompanyId(UUID companyId);

    /** Tenant-wide search over title + body (case-insensitive). */
    @Query("""
            select p from Page p
            where p.companyId = :companyId
              and (lower(p.title) like lower(concat('%', :q, '%'))
                   or lower(coalesce(p.body, '')) like lower(concat('%', :q, '%')))
            order by p.updatedAt desc
            """)
    List<Page> search(@Param("companyId") UUID companyId, @Param("q") String q);
}
