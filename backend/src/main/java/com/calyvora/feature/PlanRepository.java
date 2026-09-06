package com.calyvora.feature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, String> {

    List<Plan> findByActiveTrueOrderBySortOrderAsc();

    List<Plan> findAllByOrderBySortOrderAsc();
}
