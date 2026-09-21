package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to update payment settings for a tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaymentSettingsRequest {
    
    // Payment method toggles
    private Boolean inAppSmsEnabled;
    private Boolean stkPushEnabled;
    private Boolean manualEnabled;
    
    // STK Push configuration
    private String darajaConsumerKey;
    private String darajaConsumerSecret;
    private String businessShortCode;
    private String lipaNaMpesaPasskey;
    private String accountReference;
    private String transactionDesc;
    private String callbackUrl;
    
    // In-App SMS configuration
    private String treasurerPhone;
    private String treasurerName;
    
    @Min(value = 5, message = "Claim expiry must be at least 5 minutes")
    private Integer claimExpiryMinutes;
    
    @Min(value = 5, message = "Fallback match window must be at least 5 minutes")
    private Integer fallbackMatchWindowMinutes;
    
    // General settings
    private Boolean smsNotificationsEnabled;
    private Boolean pushNotificationsEnabled;
    private String defaultPaymentMethod;
}
