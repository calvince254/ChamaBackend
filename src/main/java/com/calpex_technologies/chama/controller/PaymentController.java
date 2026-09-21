package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.PaymentEvent;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for payment operations.
 * Handles payment claims, STK Push, SMS events, device registration, and manual verification.
 * 
 * Unified payment endpoint: POST /api/payments/initiate
 * - Works for all payment types: registration fee, fines, contributions, etc.
 * - Supports 3 payment methods: MANUAL, STK_PUSH, IN_APP_SMS
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentClaimService paymentClaimService;
    private final SmsMatchingService smsMatchingService;
    private final TreasurerDeviceService treasurerDeviceService;
    private final DarajaService darajaService;
    private final MemberRepository memberRepository;
    private final UnifiedPaymentService unifiedPaymentService;

    // ==================== UNIFIED PAYMENT ENDPOINT ====================

    /**
     * Unified payment initiation endpoint.
     * Works for all payment types: REGISTRATION_FEE, FINE, MGR_FINE, PENALTY, etc.
     * 
     * Security: Members can only pay their OWN dues. To record payment for another member,
     * the caller must have appropriate management permission (e.g., MANAGE_WELFARE_CONTRIBUTIONS).
     * 
     * Usage examples:
     * 1. Pay registration fee (MANUAL):
     *    { "paymentType": "REGISTRATION_FEE", "referenceId": 123, "paymentMethod": "MANUAL", "referenceCode": "QKJ123ABC" }
     * 
     * 2. Pay fine with STK Push:
     *    { "paymentType": "FINE", "referenceId": 456, "paymentMethod": "STK_PUSH", "phoneNumber": "254712345678" }
     * 
     * 3. Pay existing payable:
     *    { "payableId": 789, "paymentMethod": "MANUAL", "referenceCode": "XYZ789DEF" }
     */
    @PostMapping("/initiate")
    public ResponseEntity<InitiatePaymentResponse> initiatePayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InitiatePaymentRequest request) {
        log.info("Initiating payment: type={}, method={}, refId={}", 
                request.getPaymentType(), request.getPaymentMethod(), request.getReferenceId());
        
        Long payerMemberId = request.getPayerMemberId() != null 
                ? request.getPayerMemberId() 
                : principal.getMemberId();
        
        // Security check: if paying for someone else, must have appropriate permission
        if (!payerMemberId.equals(principal.getMemberId())) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            boolean hasPermission = auth.getAuthorities().stream()
                    .anyMatch(grantedAuth -> {
                        String perm = grantedAuth.getAuthority();
                        return perm.equals("FULL_ACCESS") 
                                || perm.equals("MANAGE_WELFARE_CONTRIBUTIONS")
                                || perm.equals("MANAGE_CONTRIBUTIONS")
                                || perm.equals("MANAGE_FINES")
                                || perm.equals("MANAGE_PENALTIES")
                                || perm.equals("MANAGE_MGR_CONTRIBUTIONS")
                                || perm.equals("MANAGE_LOAN_REPAYMENTS");
                    });
            
            if (!hasPermission) {
                throw new RuntimeException("You do not have permission to record payments for other members");
            }
        }
        
        InitiatePaymentResponse response = unifiedPaymentService.initiatePayment(
                principal.getTenantId(), payerMemberId, request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Confirm a payment by payableId (admin action).
     * This is a unified endpoint that works for ALL payment types.
     * Admin clicks "Confirm" on any payment awaiting confirmation.
     */
    @PostMapping("/payables/{payableId}/confirm")
    public ResponseEntity<InitiatePaymentResponse> confirmPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long payableId) {
        log.info("Confirming payment for payable {} by {}", payableId, principal.getMemberId());
        
        InitiatePaymentResponse response = unifiedPaymentService.confirmPaymentByPayableId(
                principal.getTenantId(), payableId, principal.getMemberId());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Reject a payment by payableId (admin action).
     * This is a unified endpoint that works for ALL payment types.
     */
    @PostMapping("/payables/{payableId}/reject")
    public ResponseEntity<InitiatePaymentResponse> rejectPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long payableId,
            @Valid @RequestBody RejectPaymentRequest request) {
        log.info("Rejecting payment for payable {} - reason: {}", payableId, request.getReason());
        
        // Find the claim for this payable and reject it
        InitiatePaymentResponse response = unifiedPaymentService.rejectPaymentByPayableId(
                principal.getTenantId(), payableId, principal.getMemberId(), request.getReason());
        
        return ResponseEntity.ok(response);
    }

    // ==================== PAYMENT CLAIMS ====================

    /**
     * Create a payment claim when user clicks "Pay".
     */
    @PostMapping("/claims")
    public ResponseEntity<PaymentClaimDto> createClaim(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePaymentClaimRequest request) {
        log.info("Creating payment claim for member {} payable {}", 
                principal.getMemberId(), request.getPayableId());
        PaymentClaimDto claim = paymentClaimService.createClaim(
                principal.getMemberId(), principal.getTenantId(), request);
        return ResponseEntity.ok(claim);
    }

    /**
     * Get a specific payment claim.
     */
    @GetMapping("/claims/{id}")
    public ResponseEntity<PaymentStatusResponse> getClaim(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        PaymentStatusResponse status = paymentClaimService.getClaimStatus(id);
        return ResponseEntity.ok(status);
    }

    /**
     * Poll claim status - used by mobile app while waiting for payment confirmation.
     */
    @GetMapping("/claims/{id}/status")
    public ResponseEntity<PaymentStatusResponse> getClaimStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(paymentClaimService.getClaimStatus(id));
    }

    /**
     * Update claim with reference code from payer's SMS (In-App SMS flow).
     */
    @PatchMapping("/claims/{id}/reference")
    public ResponseEntity<PaymentClaimDto> updateClaimReference(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateClaimReferenceRequest request) {
        log.info("Updating claim {} with reference {}", id, request.getReferenceCode());
        PaymentClaimDto claim = paymentClaimService.updateReference(id, request);
        return ResponseEntity.ok(claim);
    }

    /**
     * Get claims awaiting manual verification.
     */
    @GetMapping("/pending-review")
    public ResponseEntity<List<PaymentClaimDto>> getPendingReview(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<PaymentClaimDto> claims = paymentClaimService.getAwaitingManualVerification(
                principal.getTenantId());
        return ResponseEntity.ok(claims);
    }

    /**
     * Get payment history for current user.
     */
    @GetMapping("/my-claims")
    public ResponseEntity<PageResponse<PaymentClaimDto>> getMyClaims(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PaymentClaimDto> claims = paymentClaimService.getPaymentHistory(
                principal.getMemberId(), pageable);
        return ResponseEntity.ok(PageResponse.from(claims));
    }

    // ==================== STK PUSH ====================

    /**
     * Initiate STK Push payment.
     */
    @PostMapping("/stk-push")
    public ResponseEntity<StkPushResponse> initiateStkPush(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StkPushRequest request) {
        log.info("Initiating STK Push for payable {} to {}", 
                request.getPayableId(), request.getPhoneNumber());

        // First create a claim
        CreatePaymentClaimRequest claimRequest = CreatePaymentClaimRequest.builder()
                .payableId(request.getPayableId())
                .paymentMethod("STK_PUSH")
                .amount(request.getAmount())
                .build();

        PaymentClaimDto claim = paymentClaimService.createClaim(
                principal.getMemberId(), principal.getTenantId(), claimRequest);

        // Then initiate STK Push
        StkPushResponse response = paymentClaimService.initiateStkPush(
                claim.getId(), request.getPhoneNumber());
        response.setClaimId(claim.getId());

        return ResponseEntity.ok(response);
    }

    /**
     * STK Push callback from Safaricom.
     * This endpoint is called by Safaricom servers, not the mobile app.
     */
    @PostMapping("/stk-push/callback")
    public ResponseEntity<String> handleStkPushCallback(
            @RequestBody StkCallbackRequest callback) {
        log.info("Received STK Push callback: checkoutRequestId={}", 
                callback.getCheckoutRequestId());
        darajaService.processCallback(callback);
        return ResponseEntity.ok("Received");
    }

    // ==================== PAYMENT EVENTS (Treasurer SMS) ====================

    /**
     * Upload a payment event from treasurer's device.
     * Uses device API key for authentication.
     */
    @PostMapping("/events")
    public ResponseEntity<PaymentEventDto> recordPaymentEvent(
            @RequestHeader("X-Device-Key") String deviceApiKey,
            @Valid @RequestBody CreatePaymentEventRequest request) {
        log.info("Recording payment event: ref={}, amount={}", 
                request.getReferenceCode(), request.getAmount());
        PaymentEvent event = smsMatchingService.recordPaymentEvent(deviceApiKey, request);
        return ResponseEntity.ok(toEventDto(event));
    }

    /**
     * Get unmatched payment events (treasurer view).
     */
    @GetMapping("/events/unmatched")
    public ResponseEntity<PageResponse<PaymentEventDto>> getUnmatchedEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PaymentEventDto> events = smsMatchingService.getUnmatchedEvents(
                principal.getTenantId(), pageable);
        return ResponseEntity.ok(PageResponse.from(events));
    }

    /**
     * Manually match a payment event to a claim.
     */
    @PostMapping("/events/{eventId}/manual-match")
    public ResponseEntity<Void> manualMatch(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long eventId,
            @Valid @RequestBody ManualMatchRequest request) {
        log.info("Manual match: event {} to claim {} by {}", 
                eventId, request.getClaimId(), principal.getMemberId());
        smsMatchingService.manualMatch(eventId, request.getClaimId(),
                memberRepository.findById(principal.getMemberId()).orElseThrow());
        return ResponseEntity.ok().build();
    }

    // ==================== TREASURER DEVICES ====================

    /**
     * Register a treasurer device for SMS capture.
     */
    @PostMapping("/devices/register")
    public ResponseEntity<DeviceRegistrationResponse> registerDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RegisterDeviceRequest request) {
        log.info("Registering treasurer device for member {}", principal.getMemberId());
        DeviceRegistrationResponse response = treasurerDeviceService.registerDevice(
                principal.getMemberId(), principal.getTenantId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all treasurer devices for the tenant.
     */
    @GetMapping("/devices")
    public ResponseEntity<List<TreasurerDeviceDto>> getDevices(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TreasurerDeviceDto> devices = treasurerDeviceService.getDevicesForTenant(
                principal.getTenantId());
        return ResponseEntity.ok(devices);
    }

    /**
     * Revoke a treasurer device.
     */
    @PostMapping("/devices/{id}/revoke")
    public ResponseEntity<Void> revokeDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam String reason) {
        log.info("Revoking device {} - reason: {}", id, reason);
        treasurerDeviceService.revokeDevice(id, principal.getTenantId(), 
                principal.getMemberId(), reason);
        return ResponseEntity.ok().build();
    }

    // ==================== DASHBOARD ====================

    /**
     * Get payment dashboard for treasurer.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<PaymentDashboardDto> getDashboard(
            @AuthenticationPrincipal UserPrincipal principal) {
        PaymentDashboardDto dashboard = paymentClaimService.getDashboard(principal.getTenantId());
        return ResponseEntity.ok(dashboard);
    }

    // ==================== HELPERS ====================

    private PaymentEventDto toEventDto(PaymentEvent event) {
        return PaymentEventDto.builder()
                .id(event.getId())
                .treasurerDeviceId(event.getTreasurerDevice().getId())
                .treasurerDeviceName(event.getTreasurerDevice().getDeviceName())
                .treasurerName(event.getTreasurerDevice().getMember().getName())
                .payerNumber(event.getPayerNumber())
                .payerName(event.getPayerName())
                .amount(event.getAmount())
                .referenceCode(event.getReferenceCode())
                .capturedAt(event.getCapturedAt())
                .matched(event.isMatched())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
