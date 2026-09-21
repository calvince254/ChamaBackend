package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request to create a payment claim when user clicks "Pay".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentClaimRequest {
    
    @NotNull(message = "Payable ID is required")
    private Long payableId;
    
    @NotNull(message = "Payment method is required")
    private String paymentMethod;  // IN_APP_SMS, STK_PUSH, MANUAL
    
    /**
     * Phone number for STK Push (if different from member's registered phone).
     */
    private String phoneNumber;
    
    /**
     * Amount to pay (optional - defaults to payable amount).
     * Can be used for partial payments if allowed.
     */
    private BigDecimal amount;
}
