package com.calyvora.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyFileRepository extends JpaRepository<CompanyFile, UUID> {

    List<CompanyFile> findByCompanyIdAndSpaceIdOrderByCreatedAtDesc(UUID companyId, UUID spaceId);

    Optional<CompanyFile> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyIdAndSpaceId(UUID companyId, UUID spaceId);

    /** Total bytes a company keeps in the database, for the per-company cap on DB storage. */
    @Query("select coalesce(sum(f.sizeBytes), 0) from CompanyFile f where f.companyId = :companyId and f.storage = 'DB'")
    long bytesStoredInDatabase(@Param("companyId") UUID companyId);
}
