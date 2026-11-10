package com.calyvora.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SignatureLinkRepository extends JpaRepository<SignatureLink, String> {

    List<SignatureLink> findByRequestId(UUID requestId);
}
