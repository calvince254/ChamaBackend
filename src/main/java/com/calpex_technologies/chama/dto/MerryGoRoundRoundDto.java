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
public class MerryGoRoundRoundDto {
    private Long id;
    private Long cycleId;
    private String cycleName;
    private Long recipientId;
    private String recipientName;
    private String payoutDate;
    
    // Amount tracking
    private BigDecimal expectedAmount;    // What should be collected
    private BigDecimal collectedAmount;   // What has been collected from contributions
    private BigDecimal paidAmount;        // What has been paid out to recipient
    private BigDecimal payoutBalance;     // What's available to pay (collected - paid)
    
    private String status; // PENDING, PARTIAL, COMPLETED
    private int position;
    
    // Contribution tracking
    private int paidContributions;
    private int totalContributions;
    
    // Nested contributions (when viewing round details)
    private List<MerryGoRoundContributionDto> contributions;
}
