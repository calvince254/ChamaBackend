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
    @NotNull(message = "Sale amount is required")
    @Positive(message = "Sale amount must be positive")
    private BigDecimal saleAmount; // Total amount received from sale

    // For partial sales: specify units to sell (optional - if null, sells all remaining units)
    private BigDecimal unitsToSell;

    // If true, sells all remaining units regardless of unitsToSell value
    private Boolean sellAll;
    
    private String saleDate;
    
    private String notes;

    // Legacy field for backward compatibility
    @Deprecated
    private BigDecimal soldAmount;

    // Get the sale amount (handles both new and legacy field names)
    public BigDecimal getEffectiveSaleAmount() {
        return saleAmount != null ? saleAmount : soldAmount;
    }
}
