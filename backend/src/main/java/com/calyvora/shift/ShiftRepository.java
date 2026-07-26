package com.calyvora.shift;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    List<Shift> findByCompanyIdOrderByStartTimeAsc(UUID companyId);

    Optional<Shift> findByIdAndCompanyId(UUID id, UUID companyId);
}
