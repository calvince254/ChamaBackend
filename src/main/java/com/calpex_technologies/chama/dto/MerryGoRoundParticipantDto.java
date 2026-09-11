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
public class MerryGoRoundParticipantDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private Integer payoutOrder;
    private String status; // ACTIVE, REMOVED, RECEIVED_PAYOUT
    
    // Stats for this participant
    private BigDecimal totalContributed;
    private BigDecimal totalOwed;
    private BigDecimal totalFines;
    private BigDecimal totalFinesPaid;
    private Boolean hasReceivedPayout;
    private String payoutDate; // If they've received their payout
}
