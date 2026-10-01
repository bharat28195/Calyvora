package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The pay-related record behind an employee: bank details, statutory enrolments (PF / ESI /
 * professional tax) and the identity used on those filings.
 *
 * <p>Deliberately separate from {@link Employee}: the directory row is readable by every colleague,
 * and none of this ever should be. Access is self-or-HR, enforced in {@link EmployeeFinanceService}.
 */
@Entity
@Table(name = "employee_finance")
public class EmployeeFinance {

    @Id
    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "payment_mode", nullable = false, length = 20)
    private String paymentMode = "BANK_TRANSFER";

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_account_no", length = 40)
    private String bankAccountNo;

    @Column(name = "bank_ifsc", length = 20)
    private String bankIfsc;

    @Column(name = "bank_account_name", length = 120)
    private String bankAccountName;

    @Column(name = "bank_branch", length = 120)
    private String bankBranch;

    @Column(name = "pf_status", nullable = false, length = 20)
    private String pfStatus = "NOT_ELIGIBLE";

    @Column(name = "pf_number", length = 40)
    private String pfNumber;

    @Column(length = 20)
    private String uan;

    @Column(name = "pf_join_date")
    private LocalDate pfJoinDate;

    @Column(name = "pf_account_name", length = 120)
    private String pfAccountName;

    @Column(name = "esi_status", nullable = false, length = 20)
    private String esiStatus = "NOT_ELIGIBLE";

    @Column(name = "esi_number", length = 40)
    private String esiNumber;

    @Column(name = "pt_state", length = 60)
    private String ptState;

    @Column(name = "pt_location", length = 60)
    private String ptLocation;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "pan_verified", nullable = false)
    private boolean panVerified;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "parent_name", length = 120)
    private String parentName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeFinance() {
    }

    public EmployeeFinance(UUID employeeId, UUID companyId) {
        this.employeeId = employeeId;
        this.companyId = companyId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankAccountNo() {
        return bankAccountNo;
    }

    public void setBankAccountNo(String bankAccountNo) {
        this.bankAccountNo = bankAccountNo;
    }

    public String getBankIfsc() {
        return bankIfsc;
    }

    public void setBankIfsc(String bankIfsc) {
        this.bankIfsc = bankIfsc;
    }

    public String getBankAccountName() {
        return bankAccountName;
    }

    public void setBankAccountName(String bankAccountName) {
        this.bankAccountName = bankAccountName;
    }

    public String getBankBranch() {
        return bankBranch;
    }

    public void setBankBranch(String bankBranch) {
        this.bankBranch = bankBranch;
    }

    public String getPfStatus() {
        return pfStatus;
    }

    public void setPfStatus(String pfStatus) {
        this.pfStatus = pfStatus;
    }

    public String getPfNumber() {
        return pfNumber;
    }

    public void setPfNumber(String pfNumber) {
        this.pfNumber = pfNumber;
    }

    public String getUan() {
        return uan;
    }

    public void setUan(String uan) {
        this.uan = uan;
    }

    public LocalDate getPfJoinDate() {
        return pfJoinDate;
    }

    public void setPfJoinDate(LocalDate pfJoinDate) {
        this.pfJoinDate = pfJoinDate;
    }

    public String getPfAccountName() {
        return pfAccountName;
    }

    public void setPfAccountName(String pfAccountName) {
        this.pfAccountName = pfAccountName;
    }

    public String getEsiStatus() {
        return esiStatus;
    }

    public void setEsiStatus(String esiStatus) {
        this.esiStatus = esiStatus;
    }

    public String getEsiNumber() {
        return esiNumber;
    }

    public void setEsiNumber(String esiNumber) {
        this.esiNumber = esiNumber;
    }

    public String getPtState() {
        return ptState;
    }

    public void setPtState(String ptState) {
        this.ptState = ptState;
    }

    public String getPtLocation() {
        return ptLocation;
    }

    public void setPtLocation(String ptLocation) {
        this.ptLocation = ptLocation;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }

    public boolean isPanVerified() {
        return panVerified;
    }

    public void setPanVerified(boolean panVerified) {
        this.panVerified = panVerified;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }
}
