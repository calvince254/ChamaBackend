package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for recording loan payments that require verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordPaymentRequest {
    private BigDecimal amount;
    private String paymentMethod;         // MPESA, CASH, BANK_TRANSFER
    private String transactionReference;  // e.g., MPESA code (for loan payments)
    private String paymentReference;      // Alias for license payments compatibility
    private String notes;
    
    // Getter that returns transactionReference or paymentReference (whichever is set)
    public String getPaymentReference() {
        return paymentReference != null ? paymentReference : transactionReference;
    }
}
