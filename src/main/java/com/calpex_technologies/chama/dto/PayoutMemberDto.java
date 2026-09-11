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
public class PayoutMemberDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private BigDecimal sharePercentage;
    private BigDecimal contributionAmount;
    private BigDecimal payoutAmount;
    private String status;
    private String paidAt;
    private String paymentReference;
}
