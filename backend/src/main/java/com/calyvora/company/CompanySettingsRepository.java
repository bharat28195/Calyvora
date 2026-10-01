package com.calyvora.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, UUID> {
}
