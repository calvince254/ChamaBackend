package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request to create a payment event from treasurer's device (incoming M-PESA SMS).
 * This is sent from the treasurer's device when they receive payment confirmation SMS.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentEventRequest {
    
    @NotBlank(message = "Payer phone number is required")
    private String payerNumber;
    
    /**
     * Payer name as shown in the SMS.
     */
    private String payerName;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    
    @NotBlank(message = "Reference code is required")
    private String referenceCode;
    
    @NotNull(message = "Captured timestamp is required")
    private LocalDateTime capturedAt;
    
    /**
     * Raw SMS text for audit purposes.
     */
    private String rawSmsText;
}
