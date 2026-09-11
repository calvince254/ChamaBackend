package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentApprovalDto {
    private Long id;
    private Long investmentId;
    private String investmentName;
    private Long approverId;
    private String approverName;
    private String decision; // PENDING, APPROVED, REJECTED
    private String decidedAt;
    private String comments;
}
