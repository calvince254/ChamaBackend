package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicenseTransactionDto {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String transactionType;
    private BigDecimal originalAmount;
    private BigDecimal feePercent;
    private BigDecimal feeAmount;
    private LocalDate transactionDate;
    private Long referenceId;
    private String referenceType;
    private String description;
    private Long invoiceId;
    private String invoiceNumber;
    private Boolean invoiced;
}
