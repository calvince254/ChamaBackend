package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDto {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String invoiceNumber;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal contributionFees;
    private BigDecimal fineFees;
    private BigDecimal loanFees;
    private BigDecimal penaltyFees;
    private BigDecimal fixedFee;
    private BigDecimal subtotal;
    private BigDecimal adjustments;
    private String adjustmentNotes;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal balance;
    private String status;
    private LocalDate paidDate;
    private String paymentReference;
    private LocalDate paymentConfirmedDate;
    private BigDecimal claimedAmount;
    private String notes;
    private Boolean isAdhoc;
    private List<LicenseTransactionDto> transactions;
}
