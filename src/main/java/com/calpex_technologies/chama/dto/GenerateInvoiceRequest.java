package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateInvoiceRequest {
    private Long tenantId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    
    // For ad-hoc invoices
    private Boolean isAdhoc;
    private String description;
    private BigDecimal amount;
}
