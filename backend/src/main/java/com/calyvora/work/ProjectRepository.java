package com.calyvora.work;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<Project> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyId(UUID companyId);

    @org.springframework.data.jpa.repository.Query("""
            select p from Project p
            where p.companyId = :companyId
              and (lower(p.name) like lower(concat('%', :q, '%'))
                   or lower(p.key) like lower(concat('%', :q, '%')))
            order by p.createdAt desc
            """)
    java.util.List<Project> search(@org.springframework.data.repository.query.Param("companyId") UUID companyId,
                                   @org.springframework.data.repository.query.Param("q") String q,
                                   org.springframework.data.domain.Pageable pageable);

    boolean existsByCompanyIdAndKeyIgnoreCase(UUID companyId, String key);
}
