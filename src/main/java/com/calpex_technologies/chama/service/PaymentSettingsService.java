package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.EnabledPaymentMethodsDto;
import com.calpex_technologies.chama.dto.PaymentSettingsDto;
import com.calpex_technologies.chama.dto.UpdatePaymentSettingsRequest;
import com.calpex_technologies.chama.entity.PaymentMethod;
import com.calpex_technologies.chama.entity.PaymentSettings;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.PaymentSettingsRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSettingsService {

    private final PaymentSettingsRepository paymentSettingsRepository;
    private final TenantRepository tenantRepository;

    /**
     * Get payment settings for a tenant, creating defaults if none exist.
     */
    @Transactional
    public PaymentSettingsDto getSettings(Long tenantId) {
        PaymentSettings settings = getOrCreateSettings(tenantId);
        return toDto(settings);
    }

    /**
     * Update payment settings for a tenant.
     */
    @Transactional
    public PaymentSettingsDto updateSettings(Long tenantId, UpdatePaymentSettingsRequest request) {
        PaymentSettings settings = getOrCreateSettings(tenantId);

        // Update payment method toggles
        if (request.getInAppSmsEnabled() != null) {
            settings.setInAppSmsEnabled(request.getInAppSmsEnabled());
        }
        if (request.getStkPushEnabled() != null) {
            settings.setStkPushEnabled(request.getStkPushEnabled());
        }
        if (request.getManualEnabled() != null) {
            settings.setManualEnabled(request.getManualEnabled());
        }

        // Ensure at least one method is enabled
        if (!settings.isInAppSmsEnabled() && !settings.isStkPushEnabled() && !settings.isManualEnabled()) {
            settings.setManualEnabled(true);
            log.warn("At least one payment method must be enabled. Defaulting to Manual.");
        }

        // Update STK Push configuration
        if (request.getDarajaConsumerKey() != null) {
            settings.setDarajaConsumerKey(request.getDarajaConsumerKey());
        }
        if (request.getDarajaConsumerSecret() != null) {
            settings.setDarajaConsumerSecret(request.getDarajaConsumerSecret());
        }
        if (request.getBusinessShortCode() != null) {
            settings.setBusinessShortCode(request.getBusinessShortCode());
        }
        if (request.getLipaNaMpesaPasskey() != null) {
            settings.setLipaNaMpesaPasskey(request.getLipaNaMpesaPasskey());
        }
        if (request.getAccountReference() != null) {
            settings.setAccountReference(request.getAccountReference());
        }
        if (request.getTransactionDesc() != null) {
            settings.setTransactionDesc(request.getTransactionDesc());
        }
        if (request.getCallbackUrl() != null) {
            settings.setCallbackUrl(request.getCallbackUrl());
        }

        // Update In-App SMS configuration
        if (request.getTreasurerPhone() != null) {
            settings.setTreasurerPhone(request.getTreasurerPhone());
        }
        if (request.getTreasurerName() != null) {
            settings.setTreasurerName(request.getTreasurerName());
        }
        if (request.getClaimExpiryMinutes() != null) {
            settings.setClaimExpiryMinutes(request.getClaimExpiryMinutes());
        }
        if (request.getFallbackMatchWindowMinutes() != null) {
            settings.setFallbackMatchWindowMinutes(request.getFallbackMatchWindowMinutes());
        }

        // Update general settings
        if (request.getSmsNotificationsEnabled() != null) {
            settings.setSmsNotificationsEnabled(request.getSmsNotificationsEnabled());
        }
        if (request.getPushNotificationsEnabled() != null) {
            settings.setPushNotificationsEnabled(request.getPushNotificationsEnabled());
        }
        if (request.getDefaultPaymentMethod() != null) {
            try {
                PaymentMethod method = PaymentMethod.valueOf(request.getDefaultPaymentMethod());
                settings.setDefaultPaymentMethod(method);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid default payment method: {}", request.getDefaultPaymentMethod());
            }
        }

        settings = paymentSettingsRepository.save(settings);
        log.info("Updated payment settings for tenant {}", tenantId);
        
        return toDto(settings);
    }

    /**
     * Get enabled payment methods for mobile app.
     * This is a lightweight endpoint that only returns what the app needs to show the payment sheet.
     */
    @Transactional(readOnly = true)
    public EnabledPaymentMethodsDto getEnabledMethods(Long tenantId) {
        PaymentSettings settings = paymentSettingsRepository.findByTenantId(tenantId)
                .orElse(createDefaultSettings(tenantId));

        List<EnabledPaymentMethodsDto.PaymentMethodOption> methods = new ArrayList<>();

        if (settings.isInAppSmsEnabled()) {
            methods.add(EnabledPaymentMethodsDto.PaymentMethodOption.builder()
                    .code("IN_APP_SMS")
                    .name("In-App Verification")
                    .description("Pay via M-PESA and verify automatically through SMS")
                    .icon("sms")
                    .requiresPhone(false)
                    .build());
        }

        if (settings.isStkPushEnabled() && isDarajaConfigured(settings)) {
            methods.add(EnabledPaymentMethodsDto.PaymentMethodOption.builder()
                    .code("STK_PUSH")
                    .name("M-PESA STK Push")
                    .description("Receive M-PESA payment prompt on your phone")
                    .icon("phone_android")
                    .requiresPhone(true)
                    .build());
        }

        if (settings.isManualEnabled()) {
            methods.add(EnabledPaymentMethodsDto.PaymentMethodOption.builder()
                    .code("MANUAL")
                    .name("Manual Verification")
                    .description("Pay and submit reference for admin verification")
                    .icon("verified_user")
                    .requiresPhone(false)
                    .build());
        }

        // If no methods are enabled (shouldn't happen), default to manual
        if (methods.isEmpty()) {
            methods.add(EnabledPaymentMethodsDto.PaymentMethodOption.builder()
                    .code("MANUAL")
                    .name("Manual Verification")
                    .description("Pay and submit reference for admin verification")
                    .icon("verified_user")
                    .requiresPhone(false)
                    .build());
        }

        return EnabledPaymentMethodsDto.builder()
                .methods(methods)
                .defaultMethod(settings.getDefaultPaymentMethod() != null 
                        ? settings.getDefaultPaymentMethod().name() 
                        : "MANUAL")
                .treasurerPhone(settings.getTreasurerPhone())
                .treasurerName(settings.getTreasurerName())
                .inAppSmsEnabled(settings.isInAppSmsEnabled())
                .stkPushEnabled(settings.isStkPushEnabled() && isDarajaConfigured(settings))
                .manualEnabled(settings.isManualEnabled())
                .build();
    }

    /**
     * Check if STK Push is available (configured and enabled).
     */
    @Transactional(readOnly = true)
    public boolean isStkPushAvailable(Long tenantId) {
        return paymentSettingsRepository.findByTenantId(tenantId)
                .map(s -> s.isStkPushEnabled() && isDarajaConfigured(s))
                .orElse(false);
    }

    /**
     * Check if In-App SMS verification is available.
     */
    @Transactional(readOnly = true)
    public boolean isInAppSmsAvailable(Long tenantId) {
        return paymentSettingsRepository.findByTenantId(tenantId)
                .map(PaymentSettings::isInAppSmsEnabled)
                .orElse(false);
    }

    /**
     * Get raw settings entity for internal use.
     */
    @Transactional(readOnly = true)
    public PaymentSettings getSettingsEntity(Long tenantId) {
        return paymentSettingsRepository.findByTenantId(tenantId)
                .orElse(createDefaultSettings(tenantId));
    }

    /**
     * Get or create settings for a tenant.
     */
    @Transactional
    public PaymentSettings getOrCreateSettings(Long tenantId) {
        return paymentSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    Tenant tenant = tenantRepository.findById(tenantId)
                            .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
                    
                    PaymentSettings settings = PaymentSettings.builder()
                            .tenant(tenant)
                            .inAppSmsEnabled(false)
                            .stkPushEnabled(false)
                            .manualEnabled(true)
                            .claimExpiryMinutes(30)
                            .fallbackMatchWindowMinutes(20)
                            .smsNotificationsEnabled(false)
                            .pushNotificationsEnabled(true)
                            .defaultPaymentMethod(PaymentMethod.MANUAL)
                            .build();
                    
                    return paymentSettingsRepository.save(settings);
                });
    }

    private PaymentSettings createDefaultSettings(Long tenantId) {
        // Create in-memory defaults without saving
        PaymentSettings settings = new PaymentSettings();
        settings.setInAppSmsEnabled(false);
        settings.setStkPushEnabled(false);
        settings.setManualEnabled(true);
        settings.setClaimExpiryMinutes(30);
        settings.setFallbackMatchWindowMinutes(20);
        settings.setSmsNotificationsEnabled(false);
        settings.setPushNotificationsEnabled(true);
        settings.setDefaultPaymentMethod(PaymentMethod.MANUAL);
        return settings;
    }

    private boolean isDarajaConfigured(PaymentSettings settings) {
        return settings.getDarajaConsumerKey() != null && !settings.getDarajaConsumerKey().isBlank()
                && settings.getDarajaConsumerSecret() != null && !settings.getDarajaConsumerSecret().isBlank()
                && settings.getBusinessShortCode() != null && !settings.getBusinessShortCode().isBlank()
                && settings.getLipaNaMpesaPasskey() != null && !settings.getLipaNaMpesaPasskey().isBlank();
    }

    private PaymentSettingsDto toDto(PaymentSettings settings) {
        List<String> enabledMethods = new ArrayList<>();
        if (settings.isInAppSmsEnabled()) enabledMethods.add("IN_APP_SMS");
        if (settings.isStkPushEnabled() && isDarajaConfigured(settings)) enabledMethods.add("STK_PUSH");
        if (settings.isManualEnabled()) enabledMethods.add("MANUAL");

        return PaymentSettingsDto.builder()
                .id(settings.getId())
                .inAppSmsEnabled(settings.isInAppSmsEnabled())
                .stkPushEnabled(settings.isStkPushEnabled())
                .manualEnabled(settings.isManualEnabled())
                .darajaConfigured(isDarajaConfigured(settings))
                .businessShortCode(settings.getBusinessShortCode())
                .accountReference(settings.getAccountReference())
                .transactionDesc(settings.getTransactionDesc())
                .callbackUrl(settings.getCallbackUrl())
                .treasurerPhone(settings.getTreasurerPhone())
                .treasurerName(settings.getTreasurerName())
                .claimExpiryMinutes(settings.getClaimExpiryMinutes())
                .fallbackMatchWindowMinutes(settings.getFallbackMatchWindowMinutes())
                .smsNotificationsEnabled(settings.isSmsNotificationsEnabled())
                .pushNotificationsEnabled(settings.isPushNotificationsEnabled())
                .defaultPaymentMethod(settings.getDefaultPaymentMethod() != null 
                        ? settings.getDefaultPaymentMethod().name() 
                        : "MANUAL")
                .enabledMethods(enabledMethods)
                .build();
    }
}
