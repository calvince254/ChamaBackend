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
public class LoanProductDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal interestRate;
    private String interestType; // FLAT, REDUCING_BALANCE
    private BigDecimal maxAmount;
    private BigDecimal minAmount;
    private BigDecimal maxMultiplier;
    private Integer repaymentPeriod;
    private String repaymentPeriodType; // DAYS, MONTHS
    private Integer requiredGuarantors;
    
    // Penalty settings
    private boolean penaltyEnabled;
    private BigDecimal penaltyRate;
    private String penaltyType; // PERCENTAGE_OF_PRINCIPAL, PERCENTAGE_OF_OUTSTANDING, FIXED_AMOUNT
    private String penaltyFrequency; // FLAT, PER_PERIOD
    private Integer gracePeriodDays;
    
    // Approval settings
    private boolean requiresApproval;
    private Integer approvalLevels;
    private List<ApprovalLevelDto> approvalLevelConfig;
    
    private boolean active;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalLevelDto {
        private Integer level;
        private Long memberId;    // Specific member approver
        private String memberName;
        private Long roleId;      // For backward compatibility
        private String roleName;
        private String levelName;
        private boolean canReject;
    }
}
