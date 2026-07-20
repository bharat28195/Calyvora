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

    boolean existsByCompanyIdAndEmail(UUID companyId, String email);
}
