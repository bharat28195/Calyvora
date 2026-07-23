package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<Goal> findByCompanyId(UUID companyId);

    Optional<Goal> findByIdAndCompanyId(UUID id, UUID companyId);
}
