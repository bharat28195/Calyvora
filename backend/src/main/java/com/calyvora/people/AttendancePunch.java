package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One check-in/check-out pair inside a day (V70). A lunch break is the gap between two of these.
 * The day's {@link AttendanceRecord} keeps the first in and the last out; effective hours are the
 * sum of these sessions.
 */
@Entity
@Table(name = "attendance_punches")
public class AttendancePunch {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "on_date", nullable = false)
    private LocalDate date;

    @Column(name = "check_in", nullable = false)
    private LocalTime checkIn;

    /** Null while the session is still open. */
    @Column(name = "check_out")
    private LocalTime checkOut;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When the person was told they never checked out of this session (V77). */
    @Column(name = "reminded_at")
    private Instant remindedAt;

    protected AttendancePunch() {
    }

    public AttendancePunch(UUID id, UUID companyId, UUID employeeId, LocalDate date, LocalTime checkIn) {
        this.id = id;
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.date = date;
        this.checkIn = checkIn;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getEmployeeId() { return employeeId; }
    public LocalDate getDate() { return date; }
    public LocalTime getCheckIn() { return checkIn; }
    public LocalTime getCheckOut() { return checkOut; }
    public void setCheckOut(LocalTime checkOut) { this.checkOut = checkOut; }
    public Instant getRemindedAt() { return remindedAt; }
    public void setRemindedAt(Instant v) { this.remindedAt = v; }
}
