package com.calyvora.tax;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The receipt number a quarter's 24Q was acknowledged with — Form 130 Part A's summary. See V72. */
@Entity
@Table(name = "tds_quarter_returns")
public class TdsQuarterReturn {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /** e.g. 2026-27-Q3. */
    @Column(nullable = false, length = 10)
    private String quarter;

    @Column(name = "receipt_no", nullable = false, length = 16)
    private String receiptNo;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TdsQuarterReturn() {
    }

    public TdsQuarterReturn(UUID companyId, String quarter) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.quarter = quarter;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getQuarter() { return quarter; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String v) { this.receiptNo = v; }
}
