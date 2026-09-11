package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellInvestmentRequest {
    @NotNull(message = "Sold amount is required")
    @Positive(message = "Sold amount must be positive")
    private BigDecimal soldAmount;
    
    private String soldDate;
    
    private String notes;
}
