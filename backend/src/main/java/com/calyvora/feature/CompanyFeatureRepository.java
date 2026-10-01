package com.calyvora.feature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyFeatureRepository extends JpaRepository<CompanyFeature, UUID> {

    List<CompanyFeature> findByCompanyId(UUID companyId);

    Optional<CompanyFeature> findByCompanyIdAndFeature(UUID companyId, Feature feature);
}
