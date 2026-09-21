package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request to update a payment claim with the M-PESA reference code from payer's SMS.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClaimReferenceRequest {
    
    @NotBlank(message = "Reference code is required")
    private String referenceCode;
    
    @NotNull(message = "Captured timestamp is required")
    private LocalDateTime capturedAt;
    
    /**
     * Phone number the payment was sent from (for verification).
     */
    private String payerPhone;
    
    /**
     * Raw SMS text for audit (optional).
     */
    private String rawSmsText;
}
