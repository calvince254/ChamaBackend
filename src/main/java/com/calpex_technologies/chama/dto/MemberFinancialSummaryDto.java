package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comprehensive financial summary for a member
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberFinancialSummaryDto {
    
    // ==================== MEMBER INFO ====================
    private Long memberId;
    private String memberName;
    private String memberNo;
    private String email;
    private String phone;
    private String status;
    private String joinDate;
    
    // ==================== SHARE OWNERSHIP ====================
    private BigDecimal originalContributions;     // Total contributions made
    private BigDecimal adjustedContributions;     // After payouts deduction
    private BigDecimal sharePercentage;           // Current share %
    private BigDecimal currentShareValue;         // Current estimated value
    private BigDecimal totalPaidOut;              // Total received from payouts
    
    // ==================== CONTRIBUTION SUMMARY ====================
    private BigDecimal totalContributionsDue;
    private BigDecimal totalContributionsPaid;
    private BigDecimal contributionsBalance;      // Due - Paid
    private int contributionMonthsCount;
    private int contributionsPaidCount;
    private int contributionsPendingCount;
    
    // ==================== LOAN SUMMARY ====================
    private BigDecimal totalLoansBorrowed;
    private BigDecimal totalLoansRepaid;
    private BigDecimal outstandingLoanBalance;
    private int activeLoansCount;
    private int clearedLoansCount;
    
    // ==================== FINES & PENALTIES ====================
    private BigDecimal totalFinesImposed;
    private BigDecimal totalFinesPaid;
    private BigDecimal finesBalance;
    private BigDecimal totalPenaltiesPaid;
    
    // ==================== WELFARE ====================
    private BigDecimal totalWelfareContributed;
    private BigDecimal totalWelfareReceived;
    
    // ==================== MERRY-GO-ROUND ====================
    private BigDecimal totalMgrContributed;
    private BigDecimal totalMgrReceived;
    
    // ==================== RECENT TRANSACTIONS ====================
    private List<TransactionDto> recentTransactions;
    
    // ==================== PAYOUT HISTORY ====================
    private List<PayoutMemberDto> payoutHistory;
    
    // ==================== CONTRIBUTION DETAILS ====================
    private List<ContributionSummaryDto> contributionsByType;
    
    // ==================== NET POSITION ====================
    private BigDecimal netPosition;  // Total in - Total out (what they own minus what they owe)
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributionSummaryDto {
        private Long contributionTypeId;
        private String contributionTypeName;
        private BigDecimal totalDue;
        private BigDecimal totalPaid;
        private BigDecimal balance;
        private int monthsCount;
    }
}
