package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Complete share calculation result including pool breakdown and member shares
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareCalculationDto {
    
    // ==================== SHARE CALCULATION POOL ====================
    // Total contributions used for share percentage calculation
    private BigDecimal totalShareContributions;
    
    // ==================== PROFIT POOL BREAKDOWN ====================
    private BigDecimal poolContributions;      // From selected contribution types
    private BigDecimal poolFines;              // Fines collected
    private BigDecimal poolLoanInterest;       // Loan interest/profits
    private BigDecimal poolInvestmentReturns;  // Investment gains
    private BigDecimal poolPenalties;          // Penalties collected
    private BigDecimal totalPaidOut;           // Total payouts already distributed
    private BigDecimal totalPoolValue;         // Sum of all pool sources minus payouts
    
    // ==================== MEMBER SHARES ====================
    private List<MemberShareDto> memberShares;
    
    // ==================== METADATA ====================
    private String calculatedAt;
    private Boolean isConfigured; // Whether settings have been configured
}
