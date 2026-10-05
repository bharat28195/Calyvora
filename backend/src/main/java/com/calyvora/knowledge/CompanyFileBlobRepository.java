package com.calyvora.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyFileBlobRepository extends JpaRepository<CompanyFileBlob, UUID> {
}
