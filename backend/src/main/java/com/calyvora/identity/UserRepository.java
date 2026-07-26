package com.calyvora.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Tenant-scoped lookups (SD-2): callers must pass the current company id. */
    Optional<User> findByIdAndCompanyId(UUID id, UUID companyId);

    List<User> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    long countByCompanyIdAndStatus(UUID companyId, UserStatus status);

    /** Tenant-scoped directory search over name + email (for global search). */
    @org.springframework.data.jpa.repository.Query("""
            select u from User u
            where u.companyId = :companyId and u.status <> com.calyvora.identity.UserStatus.DISABLED
              and (lower(u.firstName) like lower(concat('%', :q, '%'))
                   or lower(u.lastName) like lower(concat('%', :q, '%'))
                   or lower(u.email) like lower(concat('%', :q, '%')))
            order by u.firstName asc
            """)
    List<User> search(@org.springframework.data.repository.query.Param("companyId") UUID companyId,
                      @org.springframework.data.repository.query.Param("q") String q,
                      org.springframework.data.domain.Pageable pageable);

    boolean existsByCompanyIdAndEmail(UUID companyId, String email);

    /** Paged, optionally-filtered directory listing (scales the People directory to large companies). */
    @org.springframework.data.jpa.repository.Query("""
            select u from User u
            where u.companyId = :companyId
              and (:q = ''
                   or lower(u.firstName) like lower(concat('%', :q, '%'))
                   or lower(u.lastName) like lower(concat('%', :q, '%'))
                   or lower(u.email) like lower(concat('%', :q, '%')))
            """)
    org.springframework.data.domain.Page<User> directoryPage(
            @org.springframework.data.repository.query.Param("companyId") UUID companyId,
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);
}
