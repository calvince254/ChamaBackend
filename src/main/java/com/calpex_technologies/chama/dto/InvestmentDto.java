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
public class InvestmentDto {
    private Long id;
    private String name;
    private String category;
    private BigDecimal amountInvested;
    private BigDecimal currentValue;
    private String approvalStatus;
    private String status; // ACTIVE, SOLD, WRITTEN_OFF
    private String dateInvested;
    private String description;
    
    // Computed fields
    private BigDecimal netGain;
    private BigDecimal returnPercentage;
    
    // Multi-approver fields
    private Boolean requiresApproval;
    private List<Long> approverIds; // For creating
    private List<InvestmentApprovalDto> approvals; // For viewing
    private Integer requiredApprovals;
    private Long approvalCount;
    private Long rejectionCount;
    private Long pendingCount;
    
    // Created by
    private Long createdByMemberId;
    private String createdByMemberName;
    
    // Legacy approval tracking (for final approval summary)
    private String approvedBy;
    private String approvedAt;
    private String rejectionReason;
    
    // Sell tracking
    private String soldDate;
    private BigDecimal soldAmount;
    private String soldBy;
    private String soldNotes;
    private BigDecimal finalGain;
}
