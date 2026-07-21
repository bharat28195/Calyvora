package com.calyvora.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID> {

    List<Space> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<Space> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyId(UUID companyId);

    @org.springframework.data.jpa.repository.Query("""
            select s from Space s
            where s.companyId = :companyId
              and (lower(s.name) like lower(concat('%', :q, '%'))
                   or lower(s.key) like lower(concat('%', :q, '%')))
            order by s.createdAt desc
            """)
    List<Space> search(@org.springframework.data.repository.query.Param("companyId") UUID companyId,
                       @org.springframework.data.repository.query.Param("q") String q,
                       org.springframework.data.domain.Pageable pageable);

    boolean existsByCompanyIdAndKeyIgnoreCase(UUID companyId, String key);
}
