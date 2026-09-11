package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutPreviewDto {
    private String payoutType;
    private BigDecimal totalAmount;
    private Integer memberCount;
    private List<PayoutMemberPreviewDto> memberPreviews;
    private LiquidityDto liquidity;
    private Boolean canProceed;
    private String warning;
}
