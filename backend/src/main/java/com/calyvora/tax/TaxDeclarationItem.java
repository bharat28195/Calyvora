package com.calyvora.tax;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One deduction claimed on a declaration, at the amount the employee entered.
 *
 * <p>Stored uncapped on purpose. The statutory ceiling is applied when the tax is computed, so that
 * the screen can say "you claimed ₹5,00,000, ₹1,50,000 is allowable" instead of silently trimming
 * the figure and leaving the employee to wonder where the rest went — and so that a limit raised by
 * a future Finance Act reprices declarations already on file without anybody re-entering them.
 */
@Entity
@Table(name = "tax_declaration_items")
public class TaxDeclarationItem {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TaxDeduction deduction;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    protected TaxDeclarationItem() {
    }

    public TaxDeclarationItem(UUID companyId, UUID declarationId, TaxDeduction deduction, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.declarationId = declarationId;
        this.deduction = deduction;
        setAmount(amount);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeclarationId() {
        return declarationId;
    }

    public TaxDeduction getDeduction() {
        return deduction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** Negative claims are not a thing; they would increase somebody's taxable income. */
    public void setAmount(BigDecimal amount) {
        this.amount = amount == null || amount.signum() < 0 ? BigDecimal.ZERO : amount;
    }
}
