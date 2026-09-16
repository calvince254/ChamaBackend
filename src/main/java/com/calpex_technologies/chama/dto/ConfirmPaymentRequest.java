package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {
    /**
     * Amount claimed to have been paid
     */
    private BigDecimal amount;
    
    /**
     * Payment reference (e.g., M-PESA transaction code)
     */
    private String paymentReference;
    
    /**
     * Optional notes from chama admin
     */
    private String notes;
}
