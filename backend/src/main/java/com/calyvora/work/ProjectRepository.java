package com.calyvora.work;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<Project> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyId(UUID companyId);

    boolean existsByCompanyIdAndKeyIgnoreCase(UUID companyId, String key);
}
