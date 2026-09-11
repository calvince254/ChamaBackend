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
public class ShareDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private int sharesOwned;
    private BigDecimal shareValue;
    private BigDecimal totalValue;
    private String lastPurchaseDate;
}
