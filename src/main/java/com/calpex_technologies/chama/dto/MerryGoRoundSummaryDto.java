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
public class MerryGoRoundSummaryDto {
    // Overall stats across all cycles
    private int totalCycles;
    private int activeCycles;
    private int completedCycles;
    
    private BigDecimal totalContributions;
    private BigDecimal totalPayouts;
    private BigDecimal totalFinesCollected;
    private BigDecimal totalFinesPending;
    
    // Balance = contributions + fines collected - payouts
    private BigDecimal currentBalance;
    
    // Active cycles list
    private List<MerryGoRoundCycleDto> activeCyclesList;
    
    // Recent activity
    private List<MerryGoRoundContributionDto> recentContributions;
    private List<MerryGoRoundFineDto> pendingFines;
}
