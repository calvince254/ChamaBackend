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
public class ContributionReportDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private String contributionTypeName;
    private Long contributionTypeId;
    private String month;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private String status;
    private String paidOn;
    private String dueDate;
}
