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
public class ContributionTopUpDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private Long contributionTypeId;
    private String contributionTypeName;
    private BigDecimal amount;
    private String status;
    private String requestDate;
    private String approvalDate;
    private Long approvedById;
    private String approvedByName;
    private String rejectionReason;
    private String paymentReference;
    private String notes;
    private String targetMonth;  // Format: YYYY-MM
    private String description;  // Description for the top-up
}
