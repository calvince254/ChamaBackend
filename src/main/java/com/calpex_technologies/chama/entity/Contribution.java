package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "contributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Contribution extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_type_id")
    private ContributionType contributionType;

    @Column(name = "contribution_month", nullable = false)
    private YearMonth month;

    @Column(name = "amount_due", precision = 15, scale = 2, nullable = false)
    private BigDecimal amountDue;

    @Column(name = "amount_paid", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ContributionStatus status = ContributionStatus.PENDING;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal penalty = BigDecimal.ZERO;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "penalty_applied", nullable = false)
    @Builder.Default
    private boolean penaltyApplied = false;

    public enum ContributionStatus {
        PENDING,   // Not yet due or awaiting payment
        PAID,      // Fully paid
        PARTIAL,   // Partially paid
        OVERDUE ,
        DEFAULTED// Past due date and not fully paid
    }
}
