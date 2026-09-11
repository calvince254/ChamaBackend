package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Payout represents a dividend distribution or withdrawal event.
 * It tracks the total amount distributed and references individual member payouts.
 */
@Entity
@Table(name = "payouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Payout extends TenantAwareEntity {

    public enum PayoutType {
        FULL_DISTRIBUTION,      // Year-end full distribution - everyone gets their share
        PARTIAL_DIVIDEND,       // Distribute a portion of profits
        INDIVIDUAL_WITHDRAWAL   // Single member withdrawal/exit
    }

    public enum PayoutStatus {
        DRAFT,          // Being prepared
        PENDING,        // Awaiting approval
        APPROVED,       // Approved, ready to execute
        COMPLETED,      // Fully paid out
        CANCELLED       // Cancelled
    }

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PayoutType payoutType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.DRAFT;

    @Column(length = 255)
    private String description;

    // Financial snapshot at time of payout
    @Column(name = "total_pool_value", precision = 15, scale = 2)
    private BigDecimal totalPoolValue;

    @Column(name = "liquid_cash", precision = 15, scale = 2)
    private BigDecimal liquidCash;

    @Column(name = "locked_in_investments", precision = 15, scale = 2)
    private BigDecimal lockedInInvestments;

    @Column(name = "outstanding_loans", precision = 15, scale = 2)
    private BigDecimal outstandingLoans;

    // Amount being distributed
    @Column(name = "payout_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal payoutAmount;

    // Workflow tracking
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by")
    private Member initiatedBy;

    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Member approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    // Member payouts
    @OneToMany(mappedBy = "payout", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PayoutMember> memberPayouts = new ArrayList<>();

    // Helper methods
    public void addMemberPayout(PayoutMember memberPayout) {
        memberPayouts.add(memberPayout);
        memberPayout.setPayout(this);
    }

    public void removeMemberPayout(PayoutMember memberPayout) {
        memberPayouts.remove(memberPayout);
        memberPayout.setPayout(null);
    }

    public int getMemberCount() {
        return memberPayouts.size();
    }

    public long getPaidCount() {
        return memberPayouts.stream()
                .filter(mp -> mp.getStatus() == PayoutMember.PayoutMemberStatus.PAID)
                .count();
    }
}
