package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for manual contribution top-up requests.
 * Members can request to add extra money to their contribution pool.
 * Admin approval is required before the top-up is applied.
 */
@Entity
@Table(name = "contribution_topups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ContributionTopUp extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_type_id", nullable = false)
    private ContributionType contributionType;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TopUpStatus status = TopUpStatus.PENDING;

    @Column(name = "request_date", nullable = false)
    @Builder.Default
    private LocalDateTime requestDate = LocalDateTime.now();

    @Column(name = "approval_date")
    private LocalDateTime approvalDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Member approvedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * The month for which this top-up is intended (e.g., 2026-10)
     * If null, defaults to current month when approved
     */
    @Column(name = "target_month")
    private java.time.YearMonth targetMonth;

    /**
     * Description of the top-up transaction (for display purposes)
     */
    @Column(name = "description")
    private String description;

    public enum TopUpStatus {
        PENDING,    // Awaiting admin approval
        APPROVED,   // Approved and applied to contribution
        REJECTED    // Rejected by admin
    }
}
