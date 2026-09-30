package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for contribution report transactions.
 * Shows both credits (contributions paid) and debits (welfare deductions) as separate rows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributionTransactionDto {
    private Long id;
    private String date;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private Long contributionTypeId;
    private String contributionTypeName;
    
    // Transaction details
    private String direction;  // "CREDIT" or "DEBIT"
    private BigDecimal amount;
    private String description;
    private String transactionType;  // CONTRIBUTION, WELFARE, etc.
    
    // Reference info
    private Long referenceId;
    private String referenceType;
    
    // For credits - contribution period
    private String month;  // e.g., "2026-09"
}
