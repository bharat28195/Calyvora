package com.calyvora.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRoleRepository extends JpaRepository<CompanyRole, UUID> {

    List<CompanyRole> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    Optional<CompanyRole> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<CompanyRole> findByCompanyIdAndBuiltin(UUID companyId, BuiltinRole builtin);

    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
}
