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
public class InvestmentValueHistoryDto {
    private Long id;
    private Long investmentId;
    private String valueDate;
    private BigDecimal recordedValue;
    private BigDecimal previousValue;
    private BigDecimal valueChange;
    private BigDecimal percentageChange;
    private String notes;
    private String recordedBy;
}
