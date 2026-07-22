package com.calyvora.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<Client> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyId(UUID companyId);

    /** Tenant-scoped search over name + contact (for global search). */
    @Query("""
            select c from Client c
            where c.companyId = :companyId
              and (lower(c.name) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.contactName, '')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(c.contactEmail, '')) like lower(concat('%', :q, '%')))
            order by c.createdAt desc
            """)
    List<Client> search(@Param("companyId") UUID companyId, @Param("q") String q, Pageable pageable);
}
