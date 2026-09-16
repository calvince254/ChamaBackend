package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracks individual billable transactions for license fee calculation.
 * Each time a contribution, fine, loan payment, etc. is confirmed, a record is created here.
 */
@Entity
@Table(name = "license_transactions", indexes = {
    @Index(name = "idx_license_tx_tenant", columnList = "tenant_id"),
    @Index(name = "idx_license_tx_date", columnList = "transaction_date"),
    @Index(name = "idx_license_tx_invoice", columnList = "invoice_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LicenseTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Type of transaction that generated this fee
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    /**
     * Original transaction amount (e.g., contribution amount, fine amount)
     */
    @Column(name = "original_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal originalAmount;

    /**
     * Fee percentage applied
     */
    @Column(name = "fee_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal feePercent;

    /**
     * Calculated fee amount = originalAmount * feePercent / 100
     */
    @Column(name = "fee_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal feeAmount;

    /**
     * Date of the original transaction
     */
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    /**
     * Reference to the original record (e.g., contribution_id, fine_id)
     */
    @Column(name = "reference_id")
    private Long referenceId;

    /**
     * Type of the referenced entity
     */
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    /**
     * Description of the transaction
     */
    @Column(length = 500)
    private String description;

    /**
     * Invoice this transaction is included in (null until invoiced)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    /**
     * Whether this transaction has been included in an invoice
     */
    @Column(name = "invoiced")
    @Builder.Default
    private Boolean invoiced = false;

    public enum TransactionType {
        CONTRIBUTION,       // Member contribution/savings payment
        FINE,              // Fine payment
        LOAN_DISBURSEMENT, // Loan given out
        LOAN_INTEREST,     // Interest collected from loan repayment
        PENALTY,           // Penalty payment
        MONTHLY_FIXED      // Fixed monthly fee
    }
}
