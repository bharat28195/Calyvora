package com.calyvora.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One employee's payslip for a finalised month, frozen. {@code payload} is the payslip exactly as
 * issued; the columns are the figures statutory returns are built from. See V65.
 */
@Entity
@Table(name = "payslip_snapshots")
public class PayslipSnapshot {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 7)
    private String month;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private BigDecimal gross;

    @Column(nullable = false)
    private BigDecimal net;

    @Column(name = "working_days", nullable = false)
    private int workingDays;

    @Column(name = "lop_days", nullable = false)
    private BigDecimal lopDays;

    /** Gross actually paid after loss of pay (V66). */
    @Column(name = "earned_gross", nullable = false)
    private BigDecimal earnedGross;

    @Column(name = "pf_wages")
    private BigDecimal pfWages;

    @Column(name = "employee_pf")
    private BigDecimal employeePf;

    @Column(name = "employer_eps")
    private BigDecimal employerEps;

    @Column(name = "employer_epf")
    private BigDecimal employerEpf;

    @Column(name = "esi_wages")
    private BigDecimal esiWages;

    @Column(name = "employee_esi")
    private BigDecimal employeeEsi;

    @Column(name = "employer_esi")
    private BigDecimal employerEsi;

    @Column(name = "professional_tax")
    private BigDecimal professionalTax;

    @Column(name = "pt_state", length = 4)
    private String ptState;

    @Column(name = "lwf_employee")
    private BigDecimal lwfEmployee;

    @Column(name = "lwf_employer")
    private BigDecimal lwfEmployer;

    @Column(name = "income_tax")
    private BigDecimal incomeTax;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PayslipSnapshot() {
    }

    public PayslipSnapshot(UUID companyId, String month, UUID employeeId, String payload) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.month = month;
        this.employeeId = employeeId;
        this.payload = payload;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getMonth() { return month; }
    public UUID getEmployeeId() { return employeeId; }
    public String getPayload() { return payload; }

    public BigDecimal getGross() { return gross; }
    public void setGross(BigDecimal v) { this.gross = v; }
    public BigDecimal getNet() { return net; }
    public void setNet(BigDecimal v) { this.net = v; }
    public BigDecimal getEarnedGross() { return earnedGross; }
    public void setEarnedGross(BigDecimal v) { this.earnedGross = v; }
    public int getWorkingDays() { return workingDays; }
    public void setWorkingDays(int v) { this.workingDays = v; }
    public BigDecimal getLopDays() { return lopDays; }
    public void setLopDays(BigDecimal v) { this.lopDays = v; }
    public BigDecimal getPfWages() { return pfWages; }
    public void setPfWages(BigDecimal v) { this.pfWages = v; }
    public BigDecimal getEmployeePf() { return employeePf; }
    public void setEmployeePf(BigDecimal v) { this.employeePf = v; }
    public BigDecimal getEmployerEps() { return employerEps; }
    public void setEmployerEps(BigDecimal v) { this.employerEps = v; }
    public BigDecimal getEmployerEpf() { return employerEpf; }
    public void setEmployerEpf(BigDecimal v) { this.employerEpf = v; }
    public BigDecimal getEsiWages() { return esiWages; }
    public void setEsiWages(BigDecimal v) { this.esiWages = v; }
    public BigDecimal getEmployeeEsi() { return employeeEsi; }
    public void setEmployeeEsi(BigDecimal v) { this.employeeEsi = v; }
    public BigDecimal getEmployerEsi() { return employerEsi; }
    public void setEmployerEsi(BigDecimal v) { this.employerEsi = v; }
    public BigDecimal getProfessionalTax() { return professionalTax; }
    public void setProfessionalTax(BigDecimal v) { this.professionalTax = v; }
    public String getPtState() { return ptState; }
    public void setPtState(String v) { this.ptState = v; }
    public BigDecimal getLwfEmployee() { return lwfEmployee; }
    public void setLwfEmployee(BigDecimal v) { this.lwfEmployee = v; }
    public BigDecimal getLwfEmployer() { return lwfEmployer; }
    public void setLwfEmployer(BigDecimal v) { this.lwfEmployer = v; }
    public BigDecimal getIncomeTax() { return incomeTax; }
    public void setIncomeTax(BigDecimal v) { this.incomeTax = v; }
}
