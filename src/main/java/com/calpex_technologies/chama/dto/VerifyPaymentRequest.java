package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to manually verify a payment claim (for manual verification flow).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPaymentRequest {
    
    @NotBlank(message = "Reference code is required")
    private String referenceCode;
    
    /**
     * Optional comments for the verification.
     */
    private String comments;
}
