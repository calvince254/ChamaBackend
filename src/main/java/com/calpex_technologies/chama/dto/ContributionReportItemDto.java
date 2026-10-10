package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for contribution-based report items.
 * Shows contributions directly (including PENDING ones that have no transactions yet).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributionReportItemDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private Long contributionTypeId;
    private String contributionTypeName;
    
    // Contribution details
    private String month;           // e.g., "2026-09"
    private String dueDate;         // e.g., "2026-09-15"
    private String paidOn;          // e.g., "2026-09-10" or null if not paid
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private BigDecimal balance;     // amountDue - amountPaid
    
    // Status: PENDING, PAID, PARTIAL, OVERDUE, DEFAULTED
    private String status;
}
