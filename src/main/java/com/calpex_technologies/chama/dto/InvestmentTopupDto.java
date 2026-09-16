package com.calpex_technologies.chama.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InvestmentTopupDto {
    private Long id;
    private Long investmentId;
    private BigDecimal amount;
    private LocalDate topupDate;
    private BigDecimal previousPrincipal;
    private BigDecimal newPrincipal;
    private String notes;
    private Long recordedById;
    private String recordedByName;
    private LocalDateTime recordedAt;
}
