package com.calyvora.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A finalised payroll month — its existence is the lock. See V65. */
@Entity
@Table(name = "payroll_months")
public class PayrollMonth {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 7)
    private String month;

    @Column(name = "finalized_at", nullable = false)
    private Instant finalizedAt = Instant.now();

    @Column(name = "finalized_by")
    private UUID finalizedBy;

    @Column(nullable = false)
    private int employees;

    @Column(name = "total_gross", nullable = false)
    private BigDecimal totalGross;

    @Column(name = "total_net", nullable = false)
    private BigDecimal totalNet;

    @Column(name = "total_employer", nullable = false)
    private BigDecimal totalEmployer;

    protected PayrollMonth() {
    }

    public PayrollMonth(UUID companyId, String month, UUID finalizedBy, int employees,
                        BigDecimal totalGross, BigDecimal totalNet, BigDecimal totalEmployer) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.month = month;
        this.finalizedBy = finalizedBy;
        this.employees = employees;
        this.totalGross = totalGross;
        this.totalNet = totalNet;
        this.totalEmployer = totalEmployer;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getMonth() { return month; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public UUID getFinalizedBy() { return finalizedBy; }
    public int getEmployees() { return employees; }
    public BigDecimal getTotalGross() { return totalGross; }
    public BigDecimal getTotalNet() { return totalNet; }
    public BigDecimal getTotalEmployer() { return totalEmployer; }
}
