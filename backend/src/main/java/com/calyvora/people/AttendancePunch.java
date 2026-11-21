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

    // Where the check-in and check-out happened (V86, PD-81). Null when location was off or not shared.
    @Column(name = "in_latitude") private Double inLatitude;
    @Column(name = "in_longitude") private Double inLongitude;
    @Column(name = "in_accuracy_m") private Integer inAccuracyM;
    @Column(name = "in_office") private String inOffice;
    @Column(name = "in_distance_m") private Integer inDistanceM;
    @Column(name = "out_latitude") private Double outLatitude;
    @Column(name = "out_longitude") private Double outLongitude;
    @Column(name = "out_accuracy_m") private Integer outAccuracyM;
    @Column(name = "out_office") private String outOffice;
    @Column(name = "out_distance_m") private Integer outDistanceM;

    protected AttendancePunch() {
    }

    /** Record where this session started. */
    public void placeIn(CheckinLocationService.Placed p) {
        if (p == null) return;
        inLatitude = p.latitude(); inLongitude = p.longitude(); inAccuracyM = p.accuracyM();
        inOffice = p.office(); inDistanceM = p.distanceM();
    }

    /** Record where this session ended. */
    public void placeOut(CheckinLocationService.Placed p) {
        if (p == null) return;
        outLatitude = p.latitude(); outLongitude = p.longitude(); outAccuracyM = p.accuracyM();
        outOffice = p.office(); outDistanceM = p.distanceM();
    }

    public Double getInLatitude() { return inLatitude; }
    public Double getInLongitude() { return inLongitude; }
    public Integer getInAccuracyM() { return inAccuracyM; }
    public String getInOffice() { return inOffice; }
    public Integer getInDistanceM() { return inDistanceM; }
    public Double getOutLatitude() { return outLatitude; }
    public Double getOutLongitude() { return outLongitude; }
    public Integer getOutAccuracyM() { return outAccuracyM; }
    public String getOutOffice() { return outOffice; }
    public Integer getOutDistanceM() { return outDistanceM; }

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
