package com.calyvora.tax;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The challan one month's TDS was paid to the government on — what Form 130 Part A lists against each
 * deposit. One row per salary month; months paid together carry the same BSR code, date and serial.
 * See V72.
 */
@Entity
@Table(name = "tds_challans")
public class TdsChallan {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /** The salary month (YYYY-MM) whose tax this challan paid. */
    @Column(nullable = false, length = 7)
    private String month;

    @Column(name = "bsr_code", nullable = false, length = 7)
    private String bsrCode;

    @Column(name = "deposit_date", nullable = false)
    private LocalDate depositDate;

    @Column(name = "challan_serial", nullable = false, length = 5)
    private String challanSerial;

    /** What was paid for this month, all employees together. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TdsChallan() {
    }

    public TdsChallan(UUID companyId, String month) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.month = month;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getMonth() { return month; }
    public String getBsrCode() { return bsrCode; }
    public void setBsrCode(String v) { this.bsrCode = v; }
    public LocalDate getDepositDate() { return depositDate; }
    public void setDepositDate(LocalDate v) { this.depositDate = v; }
    public String getChallanSerial() { return challanSerial; }
    public void setChallanSerial(String v) { this.challanSerial = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
}
