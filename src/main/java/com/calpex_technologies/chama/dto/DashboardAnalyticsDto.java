package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAnalyticsDto {
    
    // Summary stats
    private BigDecimal totalSavings;
    private int activeMembers;
    private BigDecimal loansOutstanding;
    private int loansNeedingAttention;
    private BigDecimal welfareBalance;
    private BigDecimal investmentValue;
    
    // Ledger balance
    private BigDecimal ledgerBalance;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    
    // Fund allocation percentages
    private BigDecimal savingsPercent;
    private BigDecimal investmentsPercent;
    private BigDecimal welfarePercent;
    private BigDecimal loansPercent;
    
    // Fund growth data (last 6 months)
    private List<FundGrowthPoint> fundGrowth;
    
    // Next meeting
    private MeetingInfo nextMeeting;
    
    // Loans needing attention
    private List<LoanAlert> loanAlerts;
    
    // Next payout (merry-go-round)
    private PayoutInfo nextPayout;
    
    // Recent transactions
    private List<TransactionInfo> recentTransactions;
    
    // Pending approvals
    private List<ApprovalInfo> pendingApprovals;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FundGrowthPoint {
        private String month;
        private BigDecimal amount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeetingInfo {
        private Long id;
        private LocalDateTime dateTime;
        private String venue;
        private String type;
        private String status;
        private int attended;
        private int totalMembers;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanAlert {
        private Long id;
        private String memberName;
        private String loanType;
        private BigDecimal amount;
        private BigDecimal overdue;
        private int daysOverdue;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayoutInfo {
        private Long id;
        private String memberName;
        private int roundNumber;
        private LocalDate dueDate;
        private int paidCount;
        private int totalCount;
        private BigDecimal paidAmount;
        private BigDecimal totalAmount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionInfo {
        private Long id;
        private LocalDate date;
        private String type;
        private String description;
        private BigDecimal amount;
        private Boolean credit;  // Changed from 'isCredit' to avoid Jackson serialization issues
        
        // Alias getter for backward compatibility
        public Boolean getIsCredit() {
            return credit;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalInfo {
        private Long id;
        private String type; // LOAN, INVESTMENT, WELFARE, etc.
        private String title;
        private String memberName;
        private BigDecimal amount;
        private String status;
        private int currentLevel;
    }
}
