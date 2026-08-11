package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The HR profile of a person — a 1:1 extension of a platform {@code User} (docs/Sprint2 §0).
 * Anemic entity; rules live in {@link EmployeeService}.
 */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "employee_no", length = 32)
    private String employeeNo;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 24)
    private EmploymentType employmentType;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "work_location", length = 120)
    private String workLocation;

    @Column(length = 40)
    private String phone;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Comma-separated skills (People OS C.2). Small set per employee; kept denormalized for simplicity. */
    @Column(length = 500)
    private String skills;

    /** Performance rating 1–5 (People OS C.2), nullable until set. */
    @Column
    private Integer rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 24)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    /** Why they are leaving, and when the exit was started (PD-20). {@link #endDate} stays the last working day. */
    @Column(name = "exit_reason", length = 200)
    private String exitReason;

    @Column(name = "exit_started_at")
    private Instant exitStartedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() {
    }

    public Employee(UUID id, UUID companyId, UUID userId) {
        this.id = id;
        this.companyId = companyId;
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public void setManagerId(UUID managerId) {
        this.managerId = managerId;
    }

    public String getWorkLocation() {
        return workLocation;
    }

    public void setWorkLocation(String workLocation) {
        this.workLocation = workLocation;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getExitReason() {
        return exitReason;
    }

    public void setExitReason(String exitReason) {
        this.exitReason = exitReason;
    }

    public Instant getExitStartedAt() {
        return exitStartedAt;
    }

    public void setExitStartedAt(Instant exitStartedAt) {
        this.exitStartedAt = exitStartedAt;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }
}
