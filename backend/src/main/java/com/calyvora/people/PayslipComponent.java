package com.calyvora.people;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One line in a company's payslip template (e.g. "Basic = 50% of gross", "Provident fund = 12% of
 * basic"). The template drives payslip generation for every employee, so payroll structure is
 * configured once per company rather than hard-coded.
 */
@Entity
@Table(name = "payslip_components")
public class PayslipComponent {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PayComponentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayComponentCalc calc;

    /** The percentage or fixed amount; null for {@link PayComponentCalc#REMAINDER}. */
    @Column
    private BigDecimal value;

    /** True on the single earning used as the base for PERCENT_OF_BASIC components (usually "Basic"). */
    @Column(name = "is_basis", nullable = false)
    private boolean basis;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PayslipComponent() {
    }

    public PayslipComponent(UUID id, UUID companyId, String name, PayComponentKind kind, PayComponentCalc calc,
                            BigDecimal value, boolean basis, int sortOrder) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.kind = kind;
        this.calc = calc;
        this.value = value;
        this.basis = basis;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public String getName() { return name; }
    public PayComponentKind getKind() { return kind; }
    public PayComponentCalc getCalc() { return calc; }
    public BigDecimal getValue() { return value; }
    public boolean isBasis() { return basis; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
