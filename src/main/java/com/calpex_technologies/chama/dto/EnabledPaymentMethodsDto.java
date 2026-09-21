package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for mobile app to know which payment methods are enabled.
 * This is fetched before showing the payment sheet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnabledPaymentMethodsDto {
    
    private List<PaymentMethodOption> methods;
    private String defaultMethod;
    private String treasurerPhone;
    private String treasurerName;
    
    // Boolean flags for easy checking in frontend
    private boolean inAppSmsEnabled;
    private boolean stkPushEnabled;
    private boolean manualEnabled;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodOption {
        private String code;           // IN_APP_SMS, STK_PUSH, MANUAL
        private String name;           // "In-App Verification", "M-PESA STK Push", "Manual Verification"
        private String description;    // Brief description
        private String icon;           // Icon name for mobile app
        private boolean requiresPhone; // Whether phone number input is needed
    }
}
