package com.calyvora.payroll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PfSettingsRepository extends JpaRepository<PfSettings, UUID> {
}
