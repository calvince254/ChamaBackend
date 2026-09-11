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
public class UpdateInvestmentValueRequest {
    @NotNull(message = "New value is required")
    @Positive(message = "Value must be positive")
    private BigDecimal newValue;
    
    private String notes;
}
