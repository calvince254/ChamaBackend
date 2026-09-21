package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for PaymentClaim entity - represents a payment claim initiated by a payer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentClaimDto {
    private Long id;
    private Long payableId;
    private PayableDto payable;        // Full payable details when needed
    
    private Long payerId;
    private String payerName;
    private String payerPhone;
    
    private BigDecimal amount;
    private String paymentMethod;      // IN_APP_SMS, STK_PUSH, MANUAL
    private String referenceCode;      // M-PESA reference code
    private LocalDateTime capturedAt;
    
    private String status;             // PENDING_SMS, PENDING_MATCH, MATCHED, etc.
    private LocalDateTime matchedAt;
    private String mpesaReceipt;
    private String errorMessage;
    private LocalDateTime expiresAt;
    
    // For STK Push
    private String checkoutRequestId;
    private String merchantRequestId;
    
    // For manual verification
    private Long verifiedById;
    private String verifiedByName;
    private LocalDateTime verifiedAt;
    private String rejectionReason;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
