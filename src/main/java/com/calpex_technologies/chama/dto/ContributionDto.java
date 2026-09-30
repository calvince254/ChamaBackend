package com.calpex_technologies.chama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ContributionDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private String month;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private String status;
    private BigDecimal penalty;
    private String paidOn;
    private String dueDate;
    private boolean penaltyApplied;
    private Long contributionTypeId;
    private String contributionTypeName;
    
    @JsonProperty("hasPendingClaim")
    private Boolean hasPendingClaim; // Changed to Boolean wrapper to ensure proper JSON serialization
    
    private Long payableId; // ID of the Payable record for confirming payments
    
    // Welfare deductions from this contribution
    private BigDecimal totalWelfareDeductions;
    private List<WelfareDeductionDto> welfareDeductions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WelfareDeductionDto {
        private Long welfareEventId;
        private String welfareEventType;
        private String beneficiaryName;
        private BigDecimal amount;
        private String date;
    }
}
