package com.calyvora.work;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByProjectIdOrderBySortOrderAscNumberAsc(UUID projectId);

    List<Task> findByProjectIdAndSprintIdIsNullOrderBySortOrderAscNumberAsc(UUID projectId);

    List<Task> findBySprintIdOrderBySortOrderAscNumberAsc(UUID sprintId);

    List<Task> findBySprintId(UUID sprintId);

    Optional<Task> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatusNot(UUID projectId, TaskStatus status);

    long countBySprintId(UUID sprintId);

    long countBySprintIdAndStatus(UUID sprintId, TaskStatus status);

    List<Task> findByAssigneeIdAndStatusNotOrderByDueDateAscCreatedAtAsc(UUID assigneeId, TaskStatus status);

    List<Task> findByCompanyId(UUID companyId);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndStatus(UUID companyId, TaskStatus status);

    long countByCompanyIdAndStatusNot(UUID companyId, TaskStatus status);

    @org.springframework.data.jpa.repository.Query("""
            select t from Task t
            where t.companyId = :companyId and lower(t.title) like lower(concat('%', :q, '%'))
            order by t.createdAt desc
            """)
    List<Task> search(@org.springframework.data.repository.query.Param("companyId") UUID companyId,
                      @org.springframework.data.repository.query.Param("q") String q,
                      org.springframework.data.domain.Pageable pageable);

    @Query("select coalesce(max(t.number), 0) from Task t where t.projectId = :projectId")
    int maxNumberForProject(@Param("projectId") UUID projectId);
}
