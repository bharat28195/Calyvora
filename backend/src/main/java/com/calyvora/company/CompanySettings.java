package com.calyvora.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 1:1 configuration for a company. The primary key is the company id. */
@Entity
@Table(name = "company_settings")
public class CompanySettings {

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    /**
     * The clock this company runs on: what "today" means, and the wall time a check-in is stamped
     * with.
     *
     * <p>Defaulted to UTC while the currency beside it defaulted to INR, which is a company that
     * exists nowhere. The consequence was not abstract — an India-based tenant checking in at 21:52
     * had "In at 16:22" written against their name, correct for UTC and wrong for them, and their
     * whole attendance record was shifted by five and a half hours. The two defaults now describe
     * the same company; anyone elsewhere sets it in Settings, as they already had to.
     */
    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(nullable = false, length = 16)
    private String locale = "en";

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    /** Optional legal/registered name shown on the payslip header (falls back to the company name). */
    @Column(name = "legal_name", length = 160)
    private String legalName;

    /** Optional company address shown on the payslip header. */
    @Column(length = 300)
    private String address;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /**
     * Sign someone out after this many minutes with no activity. Null means never, which is the
     * default and what every company had before this existed.
     *
     * <p>A company setting rather than a deployment one because the answer is a policy, not a
     * technical limit: a payroll screen open on a shared desk in an office wants fifteen minutes,
     * and a five-person startup on their own laptops wants never to be asked again.
     */
    @Column(name = "session_idle_minutes")
    private Integer sessionIdleMinutes;

    /**
     * Whether employees may still change their tax declarations.
     *
     * <p>HR opens this in April and closes it before the last payroll of the year, so the figures
     * cannot move under a run that has already been filed with the department.
     */
    @Column(name = "tax_declarations_open", nullable = false)
    private boolean taxDeclarationsOpen = true;

    /** Whether employees may upload proofs for their declarations (V71). */
    @Column(name = "tax_proofs_open", nullable = false)
    private boolean taxProofsOpen;

    /**
     * The last day for proofs (V71). From the first payroll month after it, only what HR accepted
     * reduces the tax; until then, what was declared does.
     */
    @Column(name = "tax_proof_deadline")
    private java.time.LocalDate taxProofDeadline;

    /** The person responsible for deducting tax, who signs Forms 124 and 130 (V72). */
    @Column(name = "tds_signer_name", length = 160)
    private String tdsSignerName;

    /** "son / daughter of" in the verification. */
    @Column(name = "tds_signer_parent", length = 160)
    private String tdsSignerParent;

    @Column(name = "tds_signer_designation", length = 120)
    private String tdsSignerDesignation;

    @Column(name = "tds_signer_place", length = 80)
    private String tdsSignerPlace;

    /** The CIT (TDS) whose jurisdiction the TAN falls under, printed on Form 130 Part A. */
    @Column(name = "cit_tds_address", length = 400)
    private String citTdsAddress;

    /**
     * How long a working day is, for anyone not rostered onto a shift that says otherwise (V70).
     * Effective hours below this mark the day short.
     */
    @Column(name = "work_day_minutes", nullable = false)
    private int workDayMinutes = 540;

    /** Which set of starter letter templates this company has been given (V73). */
    @Column(name = "letter_starters_seeded", nullable = false)
    private int letterStartersSeeded = 1;

    /** When the standard day starts, for anyone not rostered onto a shift. */
    @Column(name = "work_day_start", nullable = false)
    private java.time.LocalTime workDayStart = java.time.LocalTime.of(9, 30);

    /** Not checked in by shift start plus this many minutes means absent for the day. */
    @Column(name = "absent_grace_minutes", nullable = false)
    private int absentGraceMinutes = 120;

    /**
     * The first day absent-after-grace and short-day-is-half-day apply. New companies start today;
     * null switches both rules off. Both cost pay, so they never reach back before this date.
     */
    @Column(name = "attendance_rules_from")
    private java.time.LocalDate attendanceRulesFrom = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompanySettings() {
    }

    public CompanySettings(UUID companyId) {
        this.companyId = companyId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isTaxProofsOpen() {
        return taxProofsOpen;
    }

    public void setTaxProofsOpen(boolean taxProofsOpen) {
        this.taxProofsOpen = taxProofsOpen;
    }

    public java.time.LocalDate getTaxProofDeadline() {
        return taxProofDeadline;
    }

    public void setTaxProofDeadline(java.time.LocalDate taxProofDeadline) {
        this.taxProofDeadline = taxProofDeadline;
    }

    public String getTdsSignerName() { return tdsSignerName; }
    public void setTdsSignerName(String v) { this.tdsSignerName = v; }
    public String getTdsSignerParent() { return tdsSignerParent; }
    public void setTdsSignerParent(String v) { this.tdsSignerParent = v; }
    public String getTdsSignerDesignation() { return tdsSignerDesignation; }
    public void setTdsSignerDesignation(String v) { this.tdsSignerDesignation = v; }
    public String getTdsSignerPlace() { return tdsSignerPlace; }
    public void setTdsSignerPlace(String v) { this.tdsSignerPlace = v; }
    public String getCitTdsAddress() { return citTdsAddress; }
    public void setCitTdsAddress(String v) { this.citTdsAddress = v; }

    public int getWorkDayMinutes() {
        return workDayMinutes;
    }

    public int getLetterStartersSeeded() { return letterStartersSeeded; }
    public void setLetterStartersSeeded(int v) { this.letterStartersSeeded = v; }

    public void setWorkDayMinutes(int workDayMinutes) {
        this.workDayMinutes = workDayMinutes;
    }

    public java.time.LocalTime getWorkDayStart() {
        return workDayStart;
    }

    public void setWorkDayStart(java.time.LocalTime workDayStart) {
        this.workDayStart = workDayStart;
    }

    public int getAbsentGraceMinutes() {
        return absentGraceMinutes;
    }

    public void setAbsentGraceMinutes(int absentGraceMinutes) {
        this.absentGraceMinutes = absentGraceMinutes;
    }

    public java.time.LocalDate getAttendanceRulesFrom() {
        return attendanceRulesFrom;
    }

    public void setAttendanceRulesFrom(java.time.LocalDate attendanceRulesFrom) {
        this.attendanceRulesFrom = attendanceRulesFrom;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public Integer getSessionIdleMinutes() { return sessionIdleMinutes; }
    public void setSessionIdleMinutes(Integer sessionIdleMinutes) { this.sessionIdleMinutes = sessionIdleMinutes; }

    public boolean isTaxDeclarationsOpen() { return taxDeclarationsOpen; }
    public void setTaxDeclarationsOpen(boolean taxDeclarationsOpen) { this.taxDeclarationsOpen = taxDeclarationsOpen; }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
