package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for PaymentEvent entity - represents an incoming payment SMS captured from treasurer's device.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventDto {
    private Long id;
    private Long treasurerDeviceId;
    private String treasurerDeviceName;
    private String treasurerName;
    
    private String payerNumber;
    private String payerName;
    private BigDecimal amount;
    private String referenceCode;
    private LocalDateTime capturedAt;
    
    private boolean matched;
    private Long matchedClaimId;
    private Long matchedById;
    private String matchedByName;
    private LocalDateTime matchedAt;
    
    private LocalDateTime createdAt;
    
    // For manual matching - potential claims that could match this event
    private java.util.List<PaymentClaimDto> potentialMatches;
}
