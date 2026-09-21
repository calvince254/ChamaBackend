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
public class LoanEligibilityDto {
    // Contribution-based eligibility
    private BigDecimal applicantContributions;
    private BigDecimal guarantorContributions;
    private BigDecimal totalContributions;
    private BigDecimal loanAmount;
    private boolean eligible;
    private BigDecimal shortfall; // How much more is needed to qualify
    
    // Loan History & Credit Score
    private LoanHistorySummary loanHistory;
    private CreditScore creditScore;
    private List<String> eligibilityNotes; // Warnings or information messages
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanHistorySummary {
        private int totalLoansCount;           // Total number of loans ever taken
        private int completedLoansCount;       // Loans fully repaid
        private int activeLoansCount;          // Currently active loans
        private int defaultedLoansCount;       // Loans that defaulted
        private BigDecimal totalBorrowed;      // Total amount ever borrowed
        private BigDecimal totalRepaid;        // Total amount repaid
        private BigDecimal currentOutstanding; // Current outstanding balance
        private boolean hasActiveLoans;        // Whether member has active loans
        private int onTimePaymentsCount;       // Payments made on time
        private int latePaymentsCount;         // Payments made late
        private int missedPaymentsCount;       // Payments not yet made past due date
        private double repaymentRate;          // Percentage of on-time payments
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditScore {
        private int score;            // 0-100 credit score
        private String rating;        // EXCELLENT, GOOD, FAIR, POOR, NO_HISTORY
        private String ratingColor;   // For UI: green, blue, yellow, orange, red
        private String description;   // Human-readable description
        private List<String> factors; // Factors affecting the score
    }
    
    /**
     * Standalone credit score response for the /my-credit-score endpoint.
     * Returns credit score + loan history without requiring loan eligibility check.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditScoreResponse {
        private CreditScore creditScore;
        private LoanHistorySummary loanHistory;
        private String memberName;
        private BigDecimal totalContributions;
    }
}
