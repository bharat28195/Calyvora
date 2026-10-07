package com.calyvora.tax;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Salary paid and tax deducted this financial year before Orbit took over, for one employee — a new
 * joiner's previous employer (Form 12B), or the company's old payroll system. See V66.
 */
@Entity
@Table(name = "tds_opening_balances")
public class TdsOpeningBalance {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "financial_year", nullable = false, length = 7)
    private String financialYear;

    /** The last month these figures cover (YYYY-MM); Orbit takes over from the month after. */
    @Column(name = "covered_through", nullable = false, length = 7)
    private String coveredThrough;

    @Column(nullable = false)
    private BigDecimal income = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal tds = BigDecimal.ZERO;

    @Column(length = 200)
    private String note;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TdsOpeningBalance() {
    }

    public TdsOpeningBalance(UUID companyId, UUID employeeId, String financialYear) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.employeeId = employeeId;
        this.financialYear = financialYear;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getEmployeeId() { return employeeId; }
    public String getFinancialYear() { return financialYear; }
    public String getCoveredThrough() { return coveredThrough; }
    public void setCoveredThrough(String v) { this.coveredThrough = v; }
    public BigDecimal getIncome() { return income; }
    public void setIncome(BigDecimal v) { this.income = v; }
    public BigDecimal getTds() { return tds; }
    public void setTds(BigDecimal v) { this.tds = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public Instant getUpdatedAt() { return updatedAt; }
}
