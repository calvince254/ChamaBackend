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
public class InvestmentSaleDto {
    private Long id;
    private Long investmentId;
    private String saleDate;
    private BigDecimal unitsSold;
    private BigDecimal unitsRemainingAfter;
    private BigDecimal salePricePerUnit;
    private BigDecimal totalSaleAmount;
    private BigDecimal principalPortion;
    private BigDecimal realizedGain;
    private BigDecimal gainPercentage;
    private Boolean isFullSale;
    private String soldBy;
    private String notes;
    private String recordedAt;
}
