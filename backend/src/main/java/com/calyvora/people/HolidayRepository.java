package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findByCompanyIdOrderByDateAsc(UUID companyId);

    List<Holiday> findByCompanyIdAndDateBetweenOrderByDateAsc(UUID companyId, LocalDate from, LocalDate to);

    Optional<Holiday> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyId(UUID companyId);
}
