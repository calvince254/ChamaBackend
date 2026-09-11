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
public class PayoutMemberPreviewDto {
    private Long memberId;
    private String memberName;
    private BigDecimal sharePercentage;
    private BigDecimal contributionAmount;
    private BigDecimal payoutAmount;
}
