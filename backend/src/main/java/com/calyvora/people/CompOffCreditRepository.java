package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompOffCreditRepository extends JpaRepository<CompOffCredit, UUID> {

    List<CompOffCredit> findByEmployeeIdOrderByWorkedOnDesc(UUID employeeId);

    List<CompOffCredit> findByCompanyIdAndStatusOrderByWorkedOnAsc(UUID companyId, CompOffStatus status);

    List<CompOffCredit> findByEmployeeIdAndStatusOrderByWorkedOnAsc(UUID employeeId, CompOffStatus status);

    Optional<CompOffCredit> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByEmployeeIdAndWorkedOn(UUID employeeId, LocalDate workedOn);
}
