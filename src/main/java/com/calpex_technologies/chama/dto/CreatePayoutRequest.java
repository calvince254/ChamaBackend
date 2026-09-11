package com.calpex_technologies.chama.dto;

import com.calpex_technologies.chama.entity.Payout;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayoutRequest {
    private Payout.PayoutType payoutType;
    private BigDecimal amount;
    private String description;
    private Long memberId; // For individual withdrawals only
}
