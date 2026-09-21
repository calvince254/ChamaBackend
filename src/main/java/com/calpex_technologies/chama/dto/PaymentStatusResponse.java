package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response for polling payment claim status.
 * Used by mobile app to check if payment has been confirmed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {
    private Long claimId;
    private String status;             // PENDING_SMS, PENDING_MATCH, MATCHED, EXPIRED, MANUAL_REVIEW, FAILED
    private String paymentMethod;
    private BigDecimal amount;
    
    // Payable info
    private Long payableId;
    private String payableType;
    private Long payableRefId;
    private String description;
    
    // Match info (populated when MATCHED)
    private LocalDateTime matchedAt;
    private String mpesaReceipt;
    
    // Error info (populated when FAILED)
    private String errorMessage;
    
    // Timing info
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private long secondsRemaining;     // Time until expiry
    
    // Status messages for UI
    private String statusMessage;      // Human-readable status message
    private boolean isComplete;        // true if no more polling needed
    private boolean isSuccess;         // true if payment confirmed
}
