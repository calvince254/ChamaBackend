package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareSettingsDto {
    private Long id;
    
    // Which contribution types count toward share percentage
    private Set<Long> shareContributionTypeIds;
    
    // Which contribution types count toward profit pool
    private Set<Long> poolContributionTypeIds;
    
    // Profit pool toggles
    private Boolean includeFines;
    private Boolean includeLoanInterest;
    private Boolean includeInvestmentReturns;
    private Boolean includePenalties;
    private Boolean includeProcessingFees;
    
    private String lastCalculatedAt;
}
