package com.calyvora.work;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByProjectIdOrderByNumberDesc(UUID projectId);

    Optional<Ticket> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Ticket> findByCompanyId(UUID companyId);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndStatusIn(UUID companyId, List<TicketStatus> statuses);

    @Query("""
            select t from Ticket t
            where t.companyId = :companyId and lower(t.subject) like lower(concat('%', :q, '%'))
            order by t.number desc
            """)
    List<Ticket> search(@Param("companyId") UUID companyId, @Param("q") String q,
                        org.springframework.data.domain.Pageable pageable);

    @Query("select coalesce(max(t.number), 0) from Ticket t where t.projectId = :projectId")
    int maxNumberForProject(@Param("projectId") UUID projectId);
}
