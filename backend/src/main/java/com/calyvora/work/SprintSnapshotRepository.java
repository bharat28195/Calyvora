package com.calyvora.work;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SprintSnapshotRepository extends JpaRepository<SprintSnapshot, UUID> {

    List<SprintSnapshot> findBySprintIdOrderByDateAsc(UUID sprintId);

    Optional<SprintSnapshot> findBySprintIdAndDate(UUID sprintId, LocalDate date);
}
