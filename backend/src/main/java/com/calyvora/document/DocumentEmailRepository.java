package com.calyvora.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentEmailRepository extends JpaRepository<DocumentEmail, UUID> {

    List<DocumentEmail> findByDocumentIdOrderBySentAtDesc(UUID documentId);
}
