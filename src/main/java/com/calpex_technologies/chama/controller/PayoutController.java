package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;

    // ==================== LIQUIDITY ====================

    /**
     * Get current liquidity breakdown (total pool, liquid cash, locked investments, outstanding loans)
     */
    @GetMapping("/liquidity")
    public ResponseEntity<LiquidityDto> getLiquidity(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(payoutService.calculateLiquidity(principal.getTenantId()));
    }

    // ==================== PAYOUT PREVIEW ====================

    /**
     * Preview a payout distribution without creating it
     */
    @PostMapping("/preview")
    public ResponseEntity<PayoutPreviewDto> previewPayout(
            @RequestBody PayoutPreviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(payoutService.previewPayout(
                principal.getTenantId(),
                request.getPayoutType(),
                request.getAmount(),
                request.getMemberId()));
    }

    // ==================== CRUD ====================

    /**
     * Get all payouts for the tenant
     */
    @GetMapping
    public ResponseEntity<List<PayoutDto>> getPayouts(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(payoutService.getPayouts(principal.getTenantId()));
    }
    
    /**
     * Get payouts with pagination.
     * Default: 10 items per page.
     */
    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<PayoutDto>> getPayoutsPaginated(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<PayoutDto> response = payoutService.getPayoutsPaginated(
                principal.getTenantId(), status, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a single payout with member details
     */
    @GetMapping("/{id}")
    public ResponseEntity<PayoutDto> getPayout(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(payoutService.getPayout(id, principal.getTenantId()));
    }

    /**
     * Create a new payout (draft status)
     */
    @PostMapping
    public ResponseEntity<PayoutDto> createPayout(
            @RequestBody CreatePayoutRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(payoutService.createPayout(
                request,
                principal.getTenantId(),
                principal.getMemberId()));
    }

    // ==================== WORKFLOW ====================

    /**
     * Submit a draft payout for approval
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<PayoutDto> submitForApproval(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(payoutService.submitForApproval(id, principal.getTenantId()));
    }

    /**
     * Approve a pending payout
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<PayoutDto> approvePayout(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(payoutService.approvePayout(
                id,
                principal.getTenantId(),
                principal.getMemberId()));
    }

    /**
     * Cancel a payout (not allowed for completed payouts)
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<PayoutDto> cancelPayout(
            @PathVariable Long id,
            @RequestBody(required = false) CancelPayoutRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(payoutService.cancelPayout(id, principal.getTenantId(), reason));
    }

    /**
     * Mark a single member's payout as paid
     */
    @PostMapping("/{id}/members/{memberId}/paid")
    public ResponseEntity<PayoutDto> markMemberPaid(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @RequestBody(required = false) MarkPaidRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String paymentReference = request != null ? request.getPaymentReference() : null;
        return ResponseEntity.ok(payoutService.markMemberPaid(
                id,
                memberId,
                principal.getTenantId(),
                paymentReference));
    }

    /**
     * Mark all members as paid at once
     */
    @PostMapping("/{id}/mark-all-paid")
    public ResponseEntity<PayoutDto> markAllPaid(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(payoutService.markAllPaid(id, principal.getTenantId()));
    }

    // ==================== REQUEST CLASSES ====================

    public static class CancelPayoutRequest {
        private String reason;
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class MarkPaidRequest {
        private String paymentReference;
        public String getPaymentReference() { return paymentReference; }
        public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    }
}
