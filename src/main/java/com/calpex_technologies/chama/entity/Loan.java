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
@Table(name = "loans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Loan extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_product_id")
    private LoanProduct loanProduct;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principal;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "total_interest", precision = 15, scale = 2)
    private BigDecimal totalInterest; // Calculated interest over loan term

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount; // Principal + Interest

    @Column(name = "monthly_payment", precision = 15, scale = 2)
    private BigDecimal monthlyPayment; // Calculated monthly installment

    @Column(name = "balance_outstanding", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balanceOutstanding = BigDecimal.ZERO;

    @Column(name = "total_paid", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "total_penalties", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalPenalties = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LoanStatus status = LoanStatus.PENDING;

    // Approval workflow tracking
    @Column(name = "current_approval_level")
    @Builder.Default
    private Integer currentApprovalLevel = 0; // 0 = not yet submitted for approval

    @Column(name = "required_approval_levels")
    @Builder.Default
    private Integer requiredApprovalLevels = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_by_id")
    private Member appliedBy; // Member who applied (could be admin on behalf)

    @ManyToMany
    @JoinTable(
        name = "loan_guarantors",
        joinColumns = @JoinColumn(name = "loan_id"),
        inverseJoinColumns = @JoinColumn(name = "guarantor_id")
    )
    @Builder.Default
    private List<Member> guarantors = new ArrayList<>();

    @Column(name = "application_date")
    private LocalDate applicationDate;

    @Column(name = "approved_date")
    private LocalDate approvedDate; // When final approval was granted

    @Column(name = "disbursed_on")
    private LocalDate disbursedOn;

    @Column(name = "expected_completion_date")
    private LocalDate expectedCompletionDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    @Column(name = "cleared_date")
    private LocalDate clearedDate;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "rejected_by_id")
    private Long rejectedById;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    public enum LoanStatus {
        DRAFT,              // Loan application started but not submitted
        PENDING,            // Submitted, awaiting first approval
        PENDING_APPROVAL,   // Awaiting approval at current level
        APPROVED,           // All approvals complete, awaiting disbursement
        ACTIVE,             // Disbursed, repayment in progress
        OVERDUE,            // Has missed payment(s)
        DEFAULTED,          // Seriously overdue, marked as default
        CLEARED,            // Fully repaid
        REJECTED            // Rejected at any approval level
    }

    // Helper method to check if loan is fully approved
    public boolean isFullyApproved() {
        return currentApprovalLevel >= requiredApprovalLevels;
    }
}
