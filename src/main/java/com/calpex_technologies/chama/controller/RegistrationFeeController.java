package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.RegistrationFeePaymentDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.RegistrationFeeService;
import com.calpex_technologies.chama.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registration-fee")
@RequiredArgsConstructor
@Slf4j
public class RegistrationFeeController {

    private final RegistrationFeeService registrationFeeService;
    private final SecurityService securityService;

    /**
     * Get registration fee info (amount and if required)
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getRegistrationFeeInfo(@AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal != null ? principal.getTenantId() : null;
        log.info("GET /api/registration-fee/info - tenantId={}", tenantId);
        
        if (tenantId == null) {
            return ResponseEntity.ok(Map.of(
                    "amount", BigDecimal.ZERO,
                    "required", false
            ));
        }
        BigDecimal amount = registrationFeeService.getRegistrationFeeAmount(tenantId);
        boolean required = registrationFeeService.isRegistrationFeeRequired(tenantId);
        log.info("Registration fee info: amount={}, required={}", amount, required);
        
        return ResponseEntity.ok(Map.of(
                "amount", amount,
                "required", required
        ));
    }

    /**
     * Get registration fee status for a specific member
     */
    @GetMapping("/member/{memberId}")
    public ResponseEntity<RegistrationFeePaymentDto> getMemberStatus(
            @PathVariable Long memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        RegistrationFeePaymentDto dto = registrationFeeService.getMemberRegistrationFeeStatus(tenantId, memberId);
        return ResponseEntity.ok(dto);
    }

    /**
     * Get all registration fee payments (admin view)
     */
    @GetMapping
    public ResponseEntity<List<RegistrationFeePaymentDto>> getAllPayments(@AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal != null ? principal.getTenantId() : null;
        if (tenantId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(registrationFeeService.getAllPayments(tenantId));
    }

    /**
     * Get payments awaiting verification (admin view)
     */
    @GetMapping("/awaiting-verification")
    public ResponseEntity<List<RegistrationFeePaymentDto>> getAwaitingVerification(@AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal != null ? principal.getTenantId() : null;
        if (tenantId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(registrationFeeService.getAwaitingVerification(tenantId));
    }

    /**
     * Count payments awaiting verification (for badge)
     */
    @GetMapping("/awaiting-verification/count")
    public ResponseEntity<Map<String, Long>> countAwaitingVerification(@AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal != null ? principal.getTenantId() : null;
        if (tenantId == null) {
            return ResponseEntity.ok(Map.of("count", 0L));
        }
        long count = registrationFeeService.countAwaitingVerification(tenantId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Submit registration fee payment (member action)
     */
    @PostMapping("/pay")
    public ResponseEntity<RegistrationFeePaymentDto> submitPayment(
            @RequestBody PaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        Long memberId = request.memberId() != null ? request.memberId() : principal.getMemberId();
        
        RegistrationFeePaymentDto dto = registrationFeeService.submitPayment(tenantId, memberId, request.referenceNumber());
        return ResponseEntity.ok(dto);
    }

    /**
     * Verify (confirm) registration fee payment (admin action)
     * NOTE: Registration fee uses its own entity (RegistrationFeePayment), not the unified Payable system.
     * This endpoint is kept for backward compatibility. Use VERIFY_PAYMENTS permission.
     */
    @PostMapping("/{paymentId}/verify")
    public ResponseEntity<RegistrationFeePaymentDto> verifyPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        Long verifiedBy = principal.getMemberId();
        
        RegistrationFeePaymentDto dto = registrationFeeService.verifyPayment(tenantId, paymentId, verifiedBy);
        return ResponseEntity.ok(dto);
    }

    /**
     * Reject registration fee payment (admin action)
     * NOTE: Registration fee uses its own entity (RegistrationFeePayment), not the unified Payable system.
     */
    @PostMapping("/{paymentId}/reject")
    public ResponseEntity<RegistrationFeePaymentDto> rejectPayment(
            @PathVariable Long paymentId,
            @RequestBody RejectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        Long rejectedBy = principal.getMemberId();
        
        RegistrationFeePaymentDto dto = registrationFeeService.rejectPayment(tenantId, paymentId, rejectedBy, request.reason());
        return ResponseEntity.ok(dto);
    }

    // Request records
    record PaymentRequest(Long memberId, String referenceNumber) {}
    record RejectRequest(String reason) {}
}
