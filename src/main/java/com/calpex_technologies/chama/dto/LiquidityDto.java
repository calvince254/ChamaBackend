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
public class LiquidityDto {
    private BigDecimal totalPoolValue;
    private BigDecimal liquidCash;
    private BigDecimal lockedInInvestments;
    private BigDecimal outstandingLoans;
    private BigDecimal totalPaidOut;
    private Boolean hasActivePayout;
    private String calculatedAt;
}
