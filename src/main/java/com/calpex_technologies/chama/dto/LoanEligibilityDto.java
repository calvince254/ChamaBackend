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
public class LoanEligibilityDto {
    private BigDecimal applicantContributions;
    private BigDecimal guarantorContributions;
    private BigDecimal totalContributions;
    private BigDecimal loanAmount;
    private boolean eligible;
    private BigDecimal shortfall; // How much more is needed to qualify
}
