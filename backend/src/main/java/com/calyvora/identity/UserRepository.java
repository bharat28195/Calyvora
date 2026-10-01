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

    /**
     * Move the platform-owner account onto a new address, credentials and all.
     *
     * <p>Deliberately a query rather than a {@code setEmail} on {@link User}: email is globally
     * unique (SD-3) and the entity is kept anemic on purpose, so opening a setter would invite
     * changing anyone's address from anywhere. This exists for exactly one caller —
     * {@code PlatformOwnerBootstrap} reconciling a changed {@code PLATFORM_OWNER_EMAIL} — and its
     * name says so.
     */
    // Carries its own transaction rather than relying on the caller's: a @Modifying query outside
    // one throws InvalidDataAccessApiUsage, and that failure would surface at startup on a real
    // deployment. flush + clear because the caller has usually just read the row it is updating,
    // and would otherwise hold a stale copy for the rest of the transaction.
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            update User u set u.email = :email, u.passwordHash = :passwordHash,
                              u.status = com.calyvora.identity.UserStatus.ACTIVE
            where u.id = :id
            """)
    int renamePlatformOwner(@org.springframework.data.repository.query.Param("id") UUID id,
                            @org.springframework.data.repository.query.Param("email") String email,
                            @org.springframework.data.repository.query.Param("passwordHash") String passwordHash);

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
