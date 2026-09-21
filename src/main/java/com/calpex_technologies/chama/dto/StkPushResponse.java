package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from initiating an STK Push payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StkPushResponse {
    private Long claimId;
    private String checkoutRequestId;
    private String merchantRequestId;
    private String customerMessage;      // Message shown on M-PESA popup
    private String responseDescription;  // Response from Safaricom
    private boolean success;
    private String errorMessage;
}
