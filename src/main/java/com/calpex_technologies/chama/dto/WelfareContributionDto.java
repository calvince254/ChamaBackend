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
public class WelfareContributionDto {
    private Long id;
    private Long welfareEventId;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private BigDecimal amountExpected;
    private BigDecimal amountPaid;
    private Boolean paid;
    private String paidDate;
    private String paymentReference;
    
    // Contribution fund payment fields
    private Boolean paidFromContribution;
    private BigDecimal amountFromFund;
    private BigDecimal amountFromCash;
    private Long sourceContributionTypeId;
    private String sourceContributionTypeName;
    private Long sourceContributionId;
    private String paymentMethod;
    
    // Payment tracking
    private Boolean hasPendingClaim;
    private Long payableId; // ID of the Payable record for confirming payments
}
