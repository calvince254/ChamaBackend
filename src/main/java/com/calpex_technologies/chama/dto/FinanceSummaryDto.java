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
public class FinanceSummaryDto {
    private BigDecimal totalSavings;
    private BigDecimal totalLoansOutstanding;
    private BigDecimal welfareFundBalance;
    private BigDecimal investmentValue;
    private BigDecimal incomeThisMonth;
    private BigDecimal expenditureThisMonth;
}
