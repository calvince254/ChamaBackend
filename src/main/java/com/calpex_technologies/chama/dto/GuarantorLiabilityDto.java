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
public class GuarantorLiabilityDto {
    private Long loanId;
    private String borrowerName;
    private BigDecimal loanAmount;
    private BigDecimal balanceOutstanding;
    private BigDecimal memberLiability; // This member's share of the outstanding balance
    private Integer guarantorCount;
    private String loanStatus;
}
