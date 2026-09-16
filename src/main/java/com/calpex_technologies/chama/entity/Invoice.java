package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Monthly invoice for license fees.
 * Generated automatically at the end of each billing cycle based on the chama's onboarding date.
 */
@Entity
@Table(name = "invoices", indexes = {
    @Index(name = "idx_invoice_tenant", columnList = "tenant_id"),
    @Index(name = "idx_invoice_status", columnList = "status"),
    @Index(name = "idx_invoice_due_date", columnList = "due_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Unique invoice number (e.g., INV-2026-09-001)
     */
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    /**
     * Billing period start date
     */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /**
     * Billing period end date
     */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /**
     * Invoice issue date
     */
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    /**
     * Payment due date
     */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /**
     * Sum of all contribution fees
     */
    @Column(name = "contribution_fees", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal contributionFees = BigDecimal.ZERO;

    /**
     * Sum of all fine fees
     */
    @Column(name = "fine_fees", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fineFees = BigDecimal.ZERO;

    /**
     * Sum of all loan-related fees
     */
    @Column(name = "loan_fees", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal loanFees = BigDecimal.ZERO;

    /**
     * Sum of all penalty fees
     */
    @Column(name = "penalty_fees", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal penaltyFees = BigDecimal.ZERO;

    /**
     * Fixed monthly fee
     */
    @Column(name = "fixed_fee", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fixedFee = BigDecimal.ZERO;

    /**
     * Total amount before any adjustments
     */
    @Column(name = "subtotal", precision = 15, scale = 2, nullable = false)
    private BigDecimal subtotal;

    /**
     * Adjustments (credits, discounts, or additional charges)
     */
    @Column(name = "adjustments", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal adjustments = BigDecimal.ZERO;

    /**
     * Description of adjustments
     */
    @Column(name = "adjustment_notes", length = 500)
    private String adjustmentNotes;

    /**
     * Final total amount due
     */
    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    /**
     * Amount already paid
     */
    @Column(name = "amount_paid", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /**
     * Balance remaining = totalAmount - amountPaid
     */
    @Column(name = "balance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Invoice status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    /**
     * Date payment was received
     */
    @Column(name = "paid_date")
    private LocalDate paidDate;

    /**
     * Payment reference (e.g., M-PESA transaction code)
     */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /**
     * Date when chama admin confirmed payment
     */
    @Column(name = "payment_confirmed_date")
    private LocalDate paymentConfirmedDate;

    /**
     * Amount chama admin claims to have paid
     */
    @Column(name = "claimed_amount", precision = 15, scale = 2)
    private BigDecimal claimedAmount;

    /**
     * Notes from super admin
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Whether this is an ad-hoc invoice (not part of regular billing cycle)
     */
    @Column(name = "is_adhoc")
    @Builder.Default
    private Boolean isAdhoc = false;

    /**
     * Transactions included in this invoice
     */
    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY)
    @Builder.Default
    private List<LicenseTransaction> transactions = new ArrayList<>();

    public enum InvoiceStatus {
        DRAFT,                  // Being prepared, not yet sent
        SENT,                   // Sent to chama admin
        PENDING_CONFIRMATION,   // Chama admin confirmed payment, awaiting super admin confirmation
        PAID,                   // Payment confirmed by super admin
        PARTIAL,                // Partially paid
        OVERDUE,                // Past due date, not paid
        CANCELLED,              // Invoice cancelled
        VOID                    // Voided (replaced or corrected)
    }

    /**
     * Calculate and update balance
     */
    public void calculateBalance() {
        this.balance = this.totalAmount.subtract(this.amountPaid != null ? this.amountPaid : BigDecimal.ZERO);
    }

    /**
     * Calculate subtotal from fee components
     */
    public void calculateSubtotal() {
        this.subtotal = (contributionFees != null ? contributionFees : BigDecimal.ZERO)
                .add(fineFees != null ? fineFees : BigDecimal.ZERO)
                .add(loanFees != null ? loanFees : BigDecimal.ZERO)
                .add(penaltyFees != null ? penaltyFees : BigDecimal.ZERO)
                .add(fixedFee != null ? fixedFee : BigDecimal.ZERO);
    }

    /**
     * Calculate total from subtotal and adjustments
     */
    public void calculateTotal() {
        this.totalAmount = (subtotal != null ? subtotal : BigDecimal.ZERO)
                .add(adjustments != null ? adjustments : BigDecimal.ZERO);
        calculateBalance();
    }
}
