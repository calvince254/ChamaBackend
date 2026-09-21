package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to manually match a payment event to a payment claim.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualMatchRequest {
    
    @NotNull(message = "Claim ID is required")
    private Long claimId;
    
    /**
     * Optional comments for the manual match.
     */
    private String comments;
}
