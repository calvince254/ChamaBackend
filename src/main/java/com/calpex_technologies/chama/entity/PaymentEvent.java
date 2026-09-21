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
 * Payment event captured from treasurer's device (incoming payment SMS).
 * Used for the dual SMS matching in the In-App SMS verification flow.
 */
@Entity
@Table(name = "payment_event", indexes = {
    @Index(name = "idx_event_device", columnList = "treasurer_device_id"),
    @Index(name = "idx_event_tenant", columnList = "tenant_id"),
    @Index(name = "idx_event_reference", columnList = "reference_code"),
    @Index(name = "idx_event_payer", columnList = "payer_number"),
    @Index(name = "idx_event_captured", columnList = "captured_at")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent extends TenantAwareEntity {

    /**
     * The treasurer device that captured this payment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treasurer_device_id", nullable = false)
    private TreasurerDevice treasurerDevice;

    /**
     * Phone number of the person who sent the money.
     */
    @Column(name = "payer_number", nullable = false, length = 15)
    private String payerNumber;

    /**
     * Name of the payer as shown in the SMS.
     */
    @Column(name = "payer_name", length = 100)
    private String payerName;

    /**
     * Amount received.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * M-PESA reference code from the SMS.
     */
    @Column(name = "reference_code", nullable = false, length = 20)
    private String referenceCode;

    /**
     * Raw SMS text for audit purposes.
     */
    @Column(name = "raw_sms_text", columnDefinition = "TEXT")
    private String rawSmsText;

    /**
     * Timestamp when the SMS was captured on the device.
     */
    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    /**
     * The payment claim that was matched to this event (if any).
     */
    @OneToOne(mappedBy = "matchedEvent", fetch = FetchType.LAZY)
    private PaymentClaim matchedClaim;

    /**
     * Whether this event has been matched to a claim.
     */
    @Column(name = "is_matched", nullable = false)
    private boolean matched = false;

    /**
     * If manually matched, who performed the match.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_by_id")
    private Member matchedBy;

    /**
     * When the match was performed.
     */
    @Column(name = "matched_at")
    private LocalDateTime matchedAt;
}
