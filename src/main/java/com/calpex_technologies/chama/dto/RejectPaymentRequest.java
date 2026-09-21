package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to reject a payment claim.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectPaymentRequest {
    
    @NotBlank(message = "Rejection reason is required")
    private String reason;
}
