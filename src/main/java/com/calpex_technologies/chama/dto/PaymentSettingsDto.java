package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for PaymentSettings entity - admin-configurable payment options.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSettingsDto {
    private Long id;
    
    // Payment method toggles
    private boolean inAppSmsEnabled;
    private boolean stkPushEnabled;
    private boolean manualEnabled;
    
    // STK Push configuration (sensitive fields masked for display)
    private boolean darajaConfigured;      // true if consumer key/secret are set
    private String businessShortCode;
    private String accountReference;
    private String transactionDesc;
    private String callbackUrl;
    
    // In-App SMS configuration
    private String treasurerPhone;
    private String treasurerName;
    private int claimExpiryMinutes;
    private int fallbackMatchWindowMinutes;
    
    // General settings
    private boolean smsNotificationsEnabled;
    private boolean pushNotificationsEnabled;
    private String defaultPaymentMethod;   // IN_APP_SMS, STK_PUSH, MANUAL
    
    // Summary of enabled methods for mobile app
    private java.util.List<String> enabledMethods;
}
