package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByCompanyIdOrderByName(UUID companyId);

    Optional<Department> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByParentId(UUID parentId);

    List<Department> findByParentId(UUID parentId);
}
