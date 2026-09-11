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
public class PayoutDto {
    private Long id;
    private String payoutType;
    private String status;
    private String description;
    
    // Financial snapshot
    private BigDecimal totalPoolValue;
    private BigDecimal liquidCash;
    private BigDecimal lockedInInvestments;
    private BigDecimal outstandingLoans;
    private BigDecimal payoutAmount;
    
    // Workflow tracking
    private String initiatedByName;
    private String initiatedAt;
    private String approvedByName;
    private String approvedAt;
    private String completedAt;
    private String cancelledAt;
    private String cancellationReason;
    
    // Member summary
    private Integer memberCount;
    private Integer paidCount;
    
    // Member details
    private List<PayoutMemberDto> memberPayouts;
    
    private String createdAt;
}
