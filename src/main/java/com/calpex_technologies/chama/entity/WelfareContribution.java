package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "welfare_contributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WelfareContribution extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "welfare_event_id", nullable = false)
    private WelfareEvent welfareEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "amount_expected", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountExpected = BigDecimal.ZERO;

    @Column(name = "amount_paid", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "paid")
    @Builder.Default
    private Boolean paid = false;

    @Column(name = "paid_date")
    private LocalDateTime paidDate;

    @Column(name = "payment_reference")
    private String paymentReference;

    // ==================== CONTRIBUTION FUND PAYMENT ====================
    
    /**
     * Whether this welfare payment was made using contribution funds
     */
    @Column(name = "paid_from_contribution")
    @Builder.Default
    private Boolean paidFromContribution = false;

    /**
     * The contribution type from which funds were deducted
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_contribution_type_id")
    private ContributionType sourceContributionType;

    /**
     * The specific contribution record that was debited
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_contribution_id")
    private Contribution sourceContribution;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    public enum PaymentMethod {
        CASH,               // Normal cash/direct payment
        CONTRIBUTION_FUND   // Payment from member's contribution pool
    }
}
