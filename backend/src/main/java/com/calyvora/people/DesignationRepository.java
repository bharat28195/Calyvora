package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {

    List<Designation> findByCompanyIdOrderByLevelAscNameAsc(UUID companyId);

    List<Designation> findByCompanyIdAndArchivedFalseOrderByLevelAscNameAsc(UUID companyId);

    Optional<Designation> findByIdAndCompanyId(UUID id, UUID companyId);

    /** Name collision check. The database index is on {@code lower(name)}; this matches it. */
    Optional<Designation> findByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
}
