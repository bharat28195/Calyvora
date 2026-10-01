package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayslipComponentRepository extends JpaRepository<PayslipComponent, UUID> {

    List<PayslipComponent> findByCompanyIdOrderBySortOrderAsc(UUID companyId);

    void deleteByCompanyId(UUID companyId);

    boolean existsByCompanyId(UUID companyId);
}
