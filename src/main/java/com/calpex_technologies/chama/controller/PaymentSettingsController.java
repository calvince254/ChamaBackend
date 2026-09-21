package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.EnabledPaymentMethodsDto;
import com.calpex_technologies.chama.dto.PaymentSettingsDto;
import com.calpex_technologies.chama.dto.UpdatePaymentSettingsRequest;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.PaymentSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing payment settings.
 * Admin can configure which payment methods (In-App SMS, STK Push, Manual) are enabled.
 */
@RestController
@RequestMapping("/api/payment-settings")
@RequiredArgsConstructor
@Slf4j
public class PaymentSettingsController {

    private final PaymentSettingsService paymentSettingsService;

    /**
     * Get payment settings for the current tenant.
     * Returns all settings including configuration details (admin view).
     */
    @GetMapping
    public ResponseEntity<PaymentSettingsDto> getSettings(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.debug("Getting payment settings for tenant {}", principal.getTenantId());
        return ResponseEntity.ok(paymentSettingsService.getSettings(principal.getTenantId()));
    }

    /**
     * Update payment settings for the current tenant.
     * Admin can enable/disable payment methods and configure STK Push credentials.
     */
    @PutMapping
    public ResponseEntity<PaymentSettingsDto> updateSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdatePaymentSettingsRequest request) {
        log.info("Updating payment settings for tenant {} by user {}", 
                principal.getTenantId(), principal.getMemberId());
        return ResponseEntity.ok(paymentSettingsService.updateSettings(
                principal.getTenantId(), request));
    }

    /**
     * Get enabled payment methods for mobile app.
     * This is a lightweight endpoint that only returns what the app needs.
     * Called before showing the payment sheet to know which options to display.
     */
    @GetMapping("/methods")
    public ResponseEntity<EnabledPaymentMethodsDto> getEnabledMethods(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentSettingsService.getEnabledMethods(principal.getTenantId()));
    }

    /**
     * Check if STK Push is available for the tenant.
     */
    @GetMapping("/stk-push/available")
    public ResponseEntity<Boolean> isStkPushAvailable(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentSettingsService.isStkPushAvailable(principal.getTenantId()));
    }

    /**
     * Check if In-App SMS verification is available for the tenant.
     */
    @GetMapping("/in-app-sms/available")
    public ResponseEntity<Boolean> isInAppSmsAvailable(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentSettingsService.isInAppSmsAvailable(principal.getTenantId()));
    }

    /**
     * Enable or disable a specific payment method quickly.
     */
    @PatchMapping("/methods/{method}/toggle")
    public ResponseEntity<PaymentSettingsDto> toggleMethod(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String method,
            @RequestParam boolean enabled) {
        log.info("Toggling payment method {} to {} for tenant {}", 
                method, enabled, principal.getTenantId());
        
        UpdatePaymentSettingsRequest request = new UpdatePaymentSettingsRequest();
        switch (method.toUpperCase()) {
            case "IN_APP_SMS":
                request.setInAppSmsEnabled(enabled);
                break;
            case "STK_PUSH":
                request.setStkPushEnabled(enabled);
                break;
            case "MANUAL":
                request.setManualEnabled(enabled);
                break;
            default:
                return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(paymentSettingsService.updateSettings(
                principal.getTenantId(), request));
    }
}
