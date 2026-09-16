package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanIncomeSummaryDto {
    private BigDecimal totalInterestCollected;
    private BigDecimal totalProcessingFeesCollected;
    private BigDecimal totalLoanIncome;
    
    // Period-specific (for filtering)
    private BigDecimal periodInterest;
    private BigDecimal periodFees;
    private BigDecimal periodTotal;
}
