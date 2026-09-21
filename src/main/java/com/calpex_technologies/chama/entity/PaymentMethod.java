package com.calpex_technologies.chama.entity;

/**
 * Payment methods available in the system.
 * Admin can enable/disable each method per tenant.
 */
public enum PaymentMethod {
    IN_APP_SMS,         // In-app SMS verification (dual SMS matching)
    STK_PUSH,           // Safaricom M-PESA STK Push
    MANUAL              // Manual verification by admin
}
