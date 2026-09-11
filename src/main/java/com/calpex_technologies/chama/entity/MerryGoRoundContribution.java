package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "merry_go_round_contributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MerryGoRoundContribution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id", nullable = false)
    private MerryGoRoundCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private MerryGoRoundRound round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ContributionStatus status = ContributionStatus.PENDING;

    @Column(name = "payment_reference")
    private String paymentReference;

    public enum ContributionStatus {
        PENDING, PAID, LATE, WAIVED
    }

    public boolean isOverdue() {
        return status == ContributionStatus.PENDING && LocalDate.now().isAfter(dueDate);
    }
}
