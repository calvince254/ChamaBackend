package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PenaltyDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private String penaltyType; // MEETING_ABSENT, MEETING_LATE_ARRIVAL, CONTRIBUTION_LATE_PAYMENT
    private String reason;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private BigDecimal balance;
    private String status; // UNPAID, PARTIAL, PAID, WAIVED
    
    // Related meeting info (for MEETING_ABSENT)
    private Long meetingId;
    private String meetingDate;
    
    // Related contribution type (for LATE_PAYMENT)
    private Long contributionTypeId;
    private String contributionTypeName;
    private String periodMonth; // e.g. "2026-08"
    
    private String imposedAt;
    private String paidAt;
    
    private boolean hasPendingClaim; // True if there's a payment claim awaiting admin confirmation
    private Long payableId; // ID of the Payable record for confirming payments
}
