package com.calyvora.people;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Employee> findByUserId(UUID userId);

    List<Employee> findByCompanyId(UUID companyId);

    /** One row of the reporting tree: who someone is, and who they report to. */
    interface ReportingEdge {
        UUID getId();
        UUID getManagerId();
    }

    /**
     * The reporting tree and nothing else.
     *
     * <p>{@link OrgScope} walks the tree on most requests, and it only ever reads two columns. Loading
     * whole {@link Employee} entities to do that puts every field of every person through the entity
     * manager — on a thousand-person company that is megabytes read, mapped and made dirty-checkable to
     * answer "who reports to whom". This returns the two columns as a projection instead.
     */
    @Query("select e.id as id, e.managerId as managerId from Employee e where e.companyId = :companyId")
    List<ReportingEdge> findReportingEdges(UUID companyId);

    /** The people on one roster. Used instead of loading the company and filtering in memory. */
    List<Employee> findByCompanyIdAndIdIn(UUID companyId, Collection<UUID> ids);

    List<Employee> findByCompanyIdAndUserIdIn(UUID companyId, List<UUID> userIds);

    /** Everyone in one employment state — the exits screen asks for NOTICE (PD-20). */
    List<Employee> findByCompanyIdAndEmploymentStatus(UUID companyId, EmploymentStatus employmentStatus);

    long countByDepartmentId(UUID departmentId);

    List<Employee> findByDepartmentId(UUID departmentId);

    long countByManagerId(UUID managerId);

    /** For the assistant: how many people are in a given employment state (notice, onboarding...). */
    long countByCompanyIdAndEmploymentStatus(UUID companyId, EmploymentStatus employmentStatus);

    List<Employee> findByManagerId(UUID managerId);

    /** How many people hold a designation — shown next to it so archiving one is an informed choice. */
    long countByDesignationId(UUID designationId);
}
