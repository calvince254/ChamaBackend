package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Unified request to initiate payment for any payable type.
 * Works for: registration fee, fines, contributions, etc.
 * 
 * Flow:
 * 1. If payableId is provided, use existing payable
 * 2. If payableId is null, create payable on-the-fly using paymentType + referenceId
 * 3. Create PaymentClaim with selected payment method
 * 4. For MANUAL: claim created with reference code, awaits admin verification
 * 5. For STK_PUSH: initiates M-PESA prompt, auto-confirms on callback
 * 6. For IN_APP_SMS: claim created, member captures SMS, treasurer verifies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentRequest {
    
    /**
     * ID of the payable to pay (if already exists).
     */
    private Long payableId;
    
    /**
     * Type of payment: REGISTRATION_FEE, FINE, MGR_FINE, PENALTY, etc.
     * Required when payableId is null.
     */
    private String paymentType;
    
    /**
     * Reference ID in the source table:
     * - REGISTRATION_FEE: memberId (who the fee is for)
     * - FINE: fineId
     * - MGR_FINE: mgrFineId
     * - PENALTY: penaltyId
     */
    private Long referenceId;
    
    /**
     * Member who is paying (defaults to current user if null).
     * For admin paying on behalf of someone, set this to the member's ID.
     */
    private Long payerMemberId;
    
    @NotNull(message = "Payment method is required")
    private String paymentMethod;  // IN_APP_SMS, STK_PUSH, MANUAL
    
    /**
     * Phone number for STK Push (required for STK_PUSH method).
     * If null, uses member's registered phone.
     */
    private String phoneNumber;
    
    /**
     * Reference code for MANUAL payment (e.g., MPESA transaction code).
     * Required for MANUAL method.
     */
    private String referenceCode;
    
    /**
     * Amount to pay (optional - defaults to payable amount).
     */
    private BigDecimal amount;
}
