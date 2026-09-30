package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Transaction extends TenantAwareEntity {

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionDirection direction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_type")
    private String referenceType;

    /**
     * For contribution-related transactions, tracks which contribution type this belongs to.
     * Used to calculate member balances per contribution type (credits - debits).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_type_id")
    private ContributionType contributionType;

    public enum TransactionType {
        CONTRIBUTION, LOAN_DISBURSEMENT, LOAN_REPAYMENT, LOAN_INTEREST, LOAN_PENALTY, WELFARE, FINE, PENALTY, INVESTMENT, INVESTMENT_RETURN, REGISTRATION_FEE, EXPENSE, SHARE_PURCHASE, MERRY_GO_ROUND, PAYOUT, PROCESSING_FEE, OTHER
    }

    public enum TransactionDirection {
        IN, OUT
    }
}
