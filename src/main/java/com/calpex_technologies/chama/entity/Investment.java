package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "investments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Investment extends TenantAwareEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InvestmentCategory category;

    @Column(name = "amount_invested", precision = 15, scale = 2, nullable = false)
    private BigDecimal amountInvested;

    @Column(name = "current_value", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_status", nullable = false)
    @Builder.Default
    private InvestmentStatus status = InvestmentStatus.ACTIVE;

    @Column(name = "date_invested")
    private LocalDate dateInvested;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Multi-approver support
    @Column(name = "requires_approval")
    @Builder.Default
    private Boolean requiresApproval = true;

    @Column(name = "required_approvals")
    @Builder.Default
    private Integer requiredApprovals = 1; // How many approvals needed

    // Individual approvals
    @OneToMany(mappedBy = "investment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<InvestmentApproval> approvals = new ArrayList<>();

    // Created by tracking
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_member_id")
    private Member createdByMember;

    // Legacy approval tracking (for final approval summary)
    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    // Sell/Exit tracking
    @Column(name = "sold_date")
    private LocalDate soldDate;

    @Column(name = "sold_amount", precision = 15, scale = 2)
    private BigDecimal soldAmount;

    @Column(name = "sold_by")
    private String soldBy;

    @Column(name = "sold_notes", columnDefinition = "TEXT")
    private String soldNotes;

    // Value history
    @OneToMany(mappedBy = "investment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<InvestmentValueHistory> valueHistory = new ArrayList<>();

    // Principal top-ups
    @OneToMany(mappedBy = "investment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvestmentTopup> topups = new ArrayList<>();

    // Helper methods for approval tracking
    public long getApprovalCount() {
        return approvals.stream()
                .filter(a -> a.getDecision() == InvestmentApproval.ApprovalDecision.APPROVED)
                .count();
    }

    public long getRejectionCount() {
        return approvals.stream()
                .filter(a -> a.getDecision() == InvestmentApproval.ApprovalDecision.REJECTED)
                .count();
    }

    public long getPendingCount() {
        return approvals.stream()
                .filter(a -> a.getDecision() == InvestmentApproval.ApprovalDecision.PENDING)
                .count();
    }

    public boolean hasApproverVoted(Long memberId) {
        return approvals.stream()
                .anyMatch(a -> a.getApprover().getId().equals(memberId) && 
                         a.getDecision() != InvestmentApproval.ApprovalDecision.PENDING);
    }

    // Computed: Net gain/loss
    public BigDecimal getNetGain() {
        return currentValue.subtract(amountInvested);
    }

    // Computed: Return percentage
    public BigDecimal getReturnPercentage() {
        if (amountInvested.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getNetGain()
                .multiply(BigDecimal.valueOf(100))
                .divide(amountInvested, 2, java.math.RoundingMode.HALF_UP);
    }

    // Computed: Final gain if sold
    public BigDecimal getFinalGain() {
        if (soldAmount == null) {
            return null;
        }
        return soldAmount.subtract(amountInvested);
    }

    public enum InvestmentCategory {
        FIXED_DEPOSIT, MONEY_MARKET, SACCO_SHARES, LAND, BUSINESS, REAL_ESTATE, STOCKS, BONDS, AGRICULTURE, OTHER
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }

    public enum InvestmentStatus {
        ACTIVE,     // Currently held
        SOLD,       // Exited/sold
        WRITTEN_OFF // Lost value completely
    }
}
