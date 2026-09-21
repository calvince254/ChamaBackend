package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for Payable entity - represents any payment due in the system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayableDto {
    private Long id;
    private String payableType;        // CONTRIBUTION, LOAN_REPAYMENT, WELFARE_DUE, etc.
    private Long payableRefId;         // ID of the actual record in module table
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private BigDecimal amount;
    private String recipientNumber;
    private String recipientName;
    private String status;             // OUTSTANDING, AWAITING_CONFIRMATION, PAID, etc.
    private String description;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Additional context for display
    private String moduleName;         // Human-readable module name
    private String shortDescription;   // Short description for list view
}
