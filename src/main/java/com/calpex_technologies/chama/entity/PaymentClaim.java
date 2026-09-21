package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a payment claim initiated by a payer.
 * This is created when a user clicks "Pay" and selects a payment method.
 */
@Entity
@Table(name = "payment_claim", indexes = {
    @Index(name = "idx_claim_payable", columnList = "payable_id"),
    @Index(name = "idx_claim_payer", columnList = "payer_id"),
    @Index(name = "idx_claim_reference", columnList = "reference_code"),
    @Index(name = "idx_claim_status", columnList = "status"),
    @Index(name = "idx_claim_checkout", columnList = "checkout_request_id")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentClaim extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payable_id", nullable = false)
    private Payable payable;

    /**
     * The member who initiated this payment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id", nullable = false)
    private Member payer;

    /**
     * Amount being paid (may differ from payable amount for partial payments).
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * Payment method used for this claim.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    /**
     * M-PESA reference code extracted from SMS.
     */
    @Column(name = "reference_code", length = 20)
    private String referenceCode;

    /**
     * Phone number used for the payment (payer's phone).
     */
    @Column(name = "payer_phone", length = 15)
    private String payerPhone;

    /**
     * Timestamp when the SMS was captured on payer's device.
     */
    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentClaimStatus status = PaymentClaimStatus.PENDING_SMS;

    /**
     * Timestamp when the claim was matched.
     */
    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    /**
     * The payment event that matched this claim (for SMS verification).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_event_id")
    private PaymentEvent matchedEvent;

    /**
     * For STK Push: The checkout request ID from Safaricom.
     */
    @Column(name = "checkout_request_id", length = 100)
    private String checkoutRequestId;

    /**
     * For STK Push: The merchant request ID from Safaricom.
     */
    @Column(name = "merchant_request_id", length = 100)
    private String merchantRequestId;

    /**
     * M-PESA receipt number (from STK Push callback or SMS).
     */
    @Column(name = "mpesa_receipt", length = 20)
    private String mpesaReceipt;

    /**
     * Error message if payment failed.
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /**
     * Expiry time for this claim.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * For manual verification: who verified it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id")
    private Member verifiedBy;

    /**
     * For manual verification: when it was verified.
     */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * For rejection: reason provided.
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
