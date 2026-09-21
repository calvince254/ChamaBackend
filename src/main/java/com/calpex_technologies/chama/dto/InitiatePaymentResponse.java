package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response from unified payment initiation endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentResponse {
    
    private Long payableId;
    private Long claimId;
    private String paymentMethod;
    private String status;  // PENDING_VERIFICATION, STK_INITIATED, AWAITING_SMS, etc.
    private String statusMessage;
    private BigDecimal amount;
    
    /**
     * For STK_PUSH: checkout request ID for polling.
     */
    private String checkoutRequestId;
    
    /**
     * For STK_PUSH: merchant request ID.
     */
    private String merchantRequestId;
    
    /**
     * For MANUAL: indicates payment submitted and awaiting admin verification.
     */
    private boolean awaitingVerification;
    
    /**
     * Reference code provided (for MANUAL method).
     */
    private String referenceCode;
    
    /**
     * Seconds until claim expires (for polling).
     */
    private long expiresInSeconds;
}
