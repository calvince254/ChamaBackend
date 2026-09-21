package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNo;
    
    // Loan product details
    private Long loanProductId;
    private String loanProductName;
    
    // Loan amounts
    private BigDecimal principal;
    private BigDecimal interestRate;
    private int termMonths;
    private BigDecimal totalInterest;
    private BigDecimal totalAmount;
    private BigDecimal monthlyPayment;
    private BigDecimal balanceOutstanding;
    private BigDecimal totalPaid;
    private BigDecimal totalPenalties;
    
    // Processing fee
    private BigDecimal processingFee;
    private BigDecimal processingFeePaid;
    private BigDecimal netDisbursementAmount; // Amount actually given to member
    private String processingFeeDeductionType; // UPFRONT, ON_REPAYMENT
    
    private String status;
    
    // Payment tracking
    private boolean hasPendingClaim;
    private Long payableId; // ID of the Payable record for confirming payments
    private BigDecimal pendingPaymentAmount; // Amount awaiting confirmation
    
    // Guarantor approval workflow (Phase 1 - sequential)
    private Integer requiredGuarantors;
    private Integer guarantorsApprovedCount;
    private Integer currentGuarantorIndex; // 0-based index of current guarantor
    private List<String> pendingGuarantorNames; // Names of guarantors who haven't approved yet
    private String approvalProgress; // e.g., "Waiting for John Doe (1/3 guarantors)" or "Approved by 2/3, waiting for Level 2"
    
    // Approver approval workflow (Phase 2 - sequential)
    private Integer currentApprovalLevel;
    private Integer requiredApprovalLevels;
    private String pendingApprovalRole; // Name of who should approve next (or "Guarantors" during phase 1)
    private String pendingApprovalType; // "GUARANTOR" or "APPROVER"
    private List<LoanApprovalDto> approvalHistory;
    
    // Guarantors
    private List<Long> guarantorIds;
    private List<String> guarantorNames;
    
    // Application details
    private Long appliedById;
    private String appliedByName;
    private String applicationDate;
    private String purpose;
    
    // Approval & Disbursement dates
    private String approvedDate;
    private String disbursedOn;
    private String expectedCompletionDate;
    private String nextDueDate;
    private String lastPaymentDate;
    private String clearedDate;
    
    // Rejection details
    private String rejectionReason;
    private String rejectedByName;
    private String rejectedAt;
    
    // For backward compatibility
    private List<String> guarantors;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanApprovalDto {
        private String approvalType; // GUARANTOR or APPROVER
        private Integer level; // null for guarantors
        private String levelName; // "Guarantor" or "Level 1 Approval", etc.
        private String approverName;
        private String approverRole;
        private String action; // APPROVED, REJECTED, RETURNED
        private String comments;
        private String actionDate;
    }
}
