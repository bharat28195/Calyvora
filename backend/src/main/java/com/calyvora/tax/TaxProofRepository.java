package com.calyvora.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxProofRepository extends JpaRepository<TaxProof, UUID> {

    /** The files on a declaration, without their bytes. */
    @Query("select new com.calyvora.tax.TaxProofRepository$ProofInfo(p.id, p.ownerType, p.ownerId, p.fileName, "
            + "p.contentType, p.sizeBytes, p.createdAt) from TaxProof p where p.declarationId = :declarationId "
            + "order by p.createdAt")
    List<ProofInfo> infoFor(UUID declarationId);

    /** How many files each declaration has — for HR's list. */
    @Query("select p.declarationId, count(p) from TaxProof p where p.declarationId in :ids group by p.declarationId")
    List<Object[]> countsFor(Collection<UUID> ids);

    Optional<TaxProof> findByIdAndDeclarationId(UUID id, UUID declarationId);

    @Query("select p.id from TaxProof p where p.declarationId = :declarationId and p.ownerType = :ownerType "
            + "and p.ownerId = :ownerId")
    List<UUID> idsFor(UUID declarationId, String ownerType, UUID ownerId);

    record ProofInfo(UUID id, String ownerType, UUID ownerId, String fileName, String contentType,
                     int sizeBytes, Instant createdAt) {
    }
}
