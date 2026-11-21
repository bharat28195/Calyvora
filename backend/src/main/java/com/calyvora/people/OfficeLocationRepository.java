package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfficeLocationRepository extends JpaRepository<OfficeLocation, UUID> {

    List<OfficeLocation> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    Optional<OfficeLocation> findByIdAndCompanyId(UUID id, UUID companyId);
}
