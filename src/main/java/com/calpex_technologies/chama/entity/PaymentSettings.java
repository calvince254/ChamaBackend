package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Payment settings for a tenant.
 * Admin can configure which payment methods are enabled for their chama.
 */
@Entity
@Table(name = "payment_settings", indexes = {
    @Index(name = "idx_payment_settings_tenant", columnList = "tenant_id", unique = true)
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSettings extends TenantAwareEntity {

    /**
     * Whether In-App SMS verification is enabled.
     * This uses dual SMS matching (payer + treasurer devices).
     */
    @Column(name = "in_app_sms_enabled", nullable = false)
    private boolean inAppSmsEnabled = false;

    /**
     * Whether Safaricom STK Push is enabled.
     * Requires Daraja API credentials to be configured.
     */
    @Column(name = "stk_push_enabled", nullable = false)
    private boolean stkPushEnabled = false;

    /**
     * Whether Manual verification is enabled.
     * Admin/treasurer manually verifies payments.
     */
    @Column(name = "manual_enabled", nullable = false)
    private boolean manualEnabled = true;

    // ========== STK Push Configuration ==========

    /**
     * Safaricom Daraja consumer key.
     */
    @Column(name = "daraja_consumer_key", length = 100)
    private String darajaConsumerKey;

    /**
     * Safaricom Daraja consumer secret (encrypted).
     */
    @Column(name = "daraja_consumer_secret", length = 255)
    private String darajaConsumerSecret;

    /**
     * Safaricom business short code (Paybill or Till number).
     */
    @Column(name = "business_short_code", length = 20)
    private String businessShortCode;

    /**
     * Safaricom Lipa Na M-PESA passkey (encrypted).
     */
    @Column(name = "lipa_na_mpesa_passkey", length = 255)
    private String lipaNaMpesaPasskey;

    /**
     * Account reference to show on M-PESA prompt.
     */
    @Column(name = "account_reference", length = 50)
    private String accountReference;

    /**
     * Transaction description to show on M-PESA prompt.
     */
    @Column(name = "transaction_desc", length = 100)
    private String transactionDesc;

    /**
     * Callback URL for STK Push results.
     */
    @Column(name = "callback_url", length = 255)
    private String callbackUrl;

    // ========== In-App SMS Configuration ==========

    /**
     * Treasurer's phone number for receiving payments (used in recipient display).
     */
    @Column(name = "treasurer_phone", length = 15)
    private String treasurerPhone;

    /**
     * Treasurer's name for display.
     */
    @Column(name = "treasurer_name", length = 100)
    private String treasurerName;

    /**
     * Time in minutes before a payment claim expires.
     */
    @Column(name = "claim_expiry_minutes", nullable = false)
    private int claimExpiryMinutes = 30;

    /**
     * Time window in minutes for fallback amount+phone matching.
     */
    @Column(name = "fallback_match_window_minutes", nullable = false)
    private int fallbackMatchWindowMinutes = 20;

    // ========== General Settings ==========

    /**
     * Whether to send SMS notifications for payment confirmations.
     */
    @Column(name = "sms_notifications_enabled", nullable = false)
    private boolean smsNotificationsEnabled = false;

    /**
     * Whether to send push notifications for payment confirmations.
     */
    @Column(name = "push_notifications_enabled", nullable = false)
    private boolean pushNotificationsEnabled = true;

    /**
     * Default payment method to pre-select in the UI.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_payment_method", length = 20)
    private PaymentMethod defaultPaymentMethod = PaymentMethod.MANUAL;
}
