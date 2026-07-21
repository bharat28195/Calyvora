package com.calyvora.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID> {

    List<Space> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<Space> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyId(UUID companyId);

    boolean existsByCompanyIdAndKeyIgnoreCase(UUID companyId, String key);
}
