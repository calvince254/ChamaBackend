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
public class MemberShareDto {
    private Long memberId;
    private String memberName;
    private String memberNo;
    
    // Member's contribution to share calculation pool (adjusted for payouts)
    private BigDecimal contributionAmount;
    
    // Share percentage (contribution / total contributions * 100)
    private BigDecimal sharePercentage;
    
    // Value calculation
    private BigDecimal memberValue; // sharePercentage * totalPoolValue / 100
    
    // Total amount already paid out to this member
    private BigDecimal totalPaidOut;
    
    // Status
    private String status;
}
