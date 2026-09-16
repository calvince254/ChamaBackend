package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Tracks each approval/rejection action on a loan.
 * Creates an audit trail of who approved/rejected at each level and when.
 * Supports both guarantor approvals (parallel) and approver approvals (sequential).
 */
@Entity
@Table(name = "loan_approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanApproval {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_type", nullable = false, length = 20)
    @Builder.Default
    private ApprovalType approvalType = ApprovalType.APPROVER; // GUARANTOR or APPROVER
    
    @Column
    private Integer level; // Which approval level (1, 2, or 3) - null for guarantors
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private Member approver; // The member who took action
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role approverRole; // The role they were acting as (for approvers)
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalAction action;
    
    @Column(columnDefinition = "TEXT")
    private String comments; // Optional comments/reason
    
    @Column(name = "action_date", nullable = false)
    private LocalDateTime actionDate;
    
    @PrePersist
    protected void onCreate() {
        if (actionDate == null) {
            actionDate = LocalDateTime.now();
        }
    }
    
    public enum ApprovalAction {
        APPROVED,   // Approved at this level
        REJECTED,   // Rejected at this level
        RETURNED    // Returned for corrections/more info
    }
    
    public enum ApprovalType {
        GUARANTOR,  // Guarantor approval (Phase 1 - parallel)
        APPROVER    // Level-based approver (Phase 2 - sequential)
    }
}
