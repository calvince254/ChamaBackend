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
public class MerryGoRoundContributionDto {
    private Long id;
    private Long cycleId;
    private Long roundId;
    private Integer roundPosition;        // Position/order of the round (1, 2, 3, etc.)
    private String roundRecipientName;    // Who receives payout for this round
    private Long memberId;
    private String memberName;            // Who is contributing
    private BigDecimal amount;
    private String dueDate;
    private String paidDate;
    private String status; // PENDING, PAID, LATE, WAIVED
    private String paymentReference;
    private Boolean isOverdue;
    private Boolean hasFine; // Whether a fine has been imposed for this contribution
}
