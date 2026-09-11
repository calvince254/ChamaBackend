package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "merry_go_round_rounds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MerryGoRoundRound extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id", nullable = false)
    private MerryGoRoundCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Member recipient;

    @Column(name = "payout_date", nullable = false)
    private LocalDate payoutDate;

    // Legacy column - maps to old 'amount' column, used as expected amount
    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal expectedAmount;
    
    // Amount actually collected from contributions
    @Column(name = "collected_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal collectedAmount = BigDecimal.ZERO;
    
    // Amount already paid out to recipient (can be partial)
    @Column(name = "paid_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoundStatus status = RoundStatus.PENDING;

    @Column(nullable = false)
    private int position;
    
    // Helper to get balance available for payout (collected but not yet paid)
    public BigDecimal getPayoutBalance() {
        return collectedAmount.subtract(paidAmount);
    }
    
    // Check if fully paid (paid amount equals or exceeds expected)
    public boolean isFullyPaid() {
        return paidAmount.compareTo(expectedAmount) >= 0;
    }

    public enum RoundStatus {
        PENDING,      // Round not yet complete
        PARTIAL,      // Some payout made but not fully paid
        COMPLETED,     // Fully paid out
        PAID
    }
}
