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
public class LicenseDto {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private BigDecimal contributionFeePercent;
    private BigDecimal fineFeePercent;
    private BigDecimal loanFeePercent;
    private BigDecimal penaltyFeePercent;
    private BigDecimal monthlyFixedFee;
    private Integer gracePeriodDays;
    private Integer billingDayOfMonth;
    private String status;
    private BigDecimal uninvoicedAmount;
}
