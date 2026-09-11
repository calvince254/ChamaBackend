package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PayoutMember represents a single member's share in a payout event.
 * Tracks the calculated amount based on share percentage.
 */
@Entity
@Table(name = "payout_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PayoutMember extends BaseEntity {

    public enum PayoutMemberStatus {
        PENDING,    // Awaiting payment
        PAID,       // Payment completed
        CANCELLED   // Cancelled (e.g., if member exits)
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id", nullable = false)
    private Payout payout;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // Share percentage at time of payout (snapshot)
    @Column(name = "share_percentage", precision = 10, scale = 4, nullable = false)
    private BigDecimal sharePercentage;

    // Contribution amount used for share calculation (snapshot)
    @Column(name = "contribution_amount", precision = 15, scale = 2)
    private BigDecimal contributionAmount;

    // Calculated payout amount
    @Column(name = "payout_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal payoutAmount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PayoutMemberStatus status = PayoutMemberStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(length = 500)
    private String notes;
}
