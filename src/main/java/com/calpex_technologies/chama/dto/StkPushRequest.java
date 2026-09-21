package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request to initiate an STK Push payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StkPushRequest {
    
    @NotNull(message = "Payable ID is required")
    private Long payableId;
    
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
    
    /**
     * Amount to pay (optional - defaults to payable amount).
     */
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
}
