package com.calyvora.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsBySlug(String slug);

    /** The single vendor company. Absent on a deployment whose owner has not been bootstrapped yet. */
    Optional<Company> findFirstByPlatformTrue();

    /** Every agency workspace, for the platform console. */
    List<Company> findByAgencyTrueOrderByNameAsc();

    /** The companies belonging to one agency — the only rows its console may ever see. */
    List<Company> findByAgencyIdOrderByNameAsc(UUID agencyId);
}
