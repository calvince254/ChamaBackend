package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettingsDto {
    
    private Long id;
    
    /**
     * Platform name displayed on invoices and throughout the system
     */
    private String platformName;
    
    /**
     * Platform contact email
     */
    private String platformEmail;
    
    /**
     * Platform contact phone
     */
    private String platformPhone;
    
    /**
     * M-PESA paybill number for payments
     */
    private String mpesaPaybill;
    
    /**
     * Bank name for payments
     */
    private String bankName;
    
    /**
     * Bank account name
     */
    private String bankAccountName;
    
    /**
     * Bank account number
     */
    private String bankAccountNumber;
    
    /**
     * Token expiry time in hours
     */
    private Integer tokenExpiryHours;

    /**
     * Minimum allowed value
     */
    private Integer minTokenExpiryHours;

    /**
     * Maximum allowed value
     */
    private Integer maxTokenExpiryHours;
}
