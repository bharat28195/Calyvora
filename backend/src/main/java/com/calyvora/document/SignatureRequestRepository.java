package com.calyvora.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SignatureRequestRepository extends JpaRepository<SignatureRequest, UUID> {

    List<SignatureRequest> findByDocumentIdOrderBySentAtDesc(UUID documentId);

    List<SignatureRequest> findByDocumentIdInOrderBySentAtDesc(Collection<UUID> documentIds);

    List<SignatureRequest> findByEmployeeIdOrderBySentAtDesc(UUID employeeId);

    Optional<SignatureRequest> findByIdAndCompanyId(UUID id, UUID companyId);
}
