package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A departing employee's full and final settlement (PD-73, V81). */
@Entity
@Table(name = "settlements")
public class Settlement {

    public enum Status { DRAFT, APPROVED, PAID }

    @Id
    private UUID id;
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    @Column(nullable = false, length = 12)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status = Status.DRAFT;
    @Column(name = "encash_types", nullable = false, length = 100)
    private String encashTypes = "VACATION";
    @Column(name = "encash_days")
    private BigDecimal encashDays;
    @Column(name = "encash_basis", nullable = false, length = 12)
    private String encashBasis = "BASIC_26";
    @Column(name = "notice_days")
    private Integer noticeDays;
    @Column(name = "notice_waived", nullable = false)
    private boolean noticeWaived;
    @Column(name = "include_gratuity", nullable = false)
    private boolean includeGratuity = true;
    @Column(name = "extra_lines", columnDefinition = "text")
    private String extraLines;
    @Column(length = 1000)
    private String note;
    @Column(name = "statement_json", columnDefinition = "text")
    private String statementJson;
    @Column(name = "net_payable")
    private BigDecimal netPayable;
    @Column(name = "approved_by")
    private UUID approvedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "document_id")
    private UUID documentId;
    @Column(name = "paid_on")
    private LocalDate paidOn;
    @Column(name = "paid_reference", length = 120)
    private String paidReference;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Settlement() {
    }

    public Settlement(UUID companyId, UUID employeeId) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.employeeId = employeeId;
    }

    public void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getEmployeeId() { return employeeId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getEncashTypes() { return encashTypes; }
    public void setEncashTypes(String encashTypes) { this.encashTypes = encashTypes; }
    public BigDecimal getEncashDays() { return encashDays; }
    public void setEncashDays(BigDecimal encashDays) { this.encashDays = encashDays; }
    public String getEncashBasis() { return encashBasis; }
    public void setEncashBasis(String encashBasis) { this.encashBasis = encashBasis; }
    public Integer getNoticeDays() { return noticeDays; }
    public void setNoticeDays(Integer noticeDays) { this.noticeDays = noticeDays; }
    public boolean isNoticeWaived() { return noticeWaived; }
    public void setNoticeWaived(boolean noticeWaived) { this.noticeWaived = noticeWaived; }
    public boolean isIncludeGratuity() { return includeGratuity; }
    public void setIncludeGratuity(boolean includeGratuity) { this.includeGratuity = includeGratuity; }
    public String getExtraLines() { return extraLines; }
    public void setExtraLines(String extraLines) { this.extraLines = extraLines; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getStatementJson() { return statementJson; }
    public void setStatementJson(String statementJson) { this.statementJson = statementJson; }
    public BigDecimal getNetPayable() { return netPayable; }
    public void setNetPayable(BigDecimal netPayable) { this.netPayable = netPayable; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void approved(UUID by, Instant at) { this.approvedBy = by; this.approvedAt = at; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public LocalDate getPaidOn() { return paidOn; }
    public String getPaidReference() { return paidReference; }
    public void paid(LocalDate on, String reference) { this.paidOn = on; this.paidReference = reference; }
    public Instant getUpdatedAt() { return updatedAt; }
}
