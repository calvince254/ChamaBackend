package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing payment claims.
 * A claim is created when a user initiates a payment through any of the 3 methods.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentClaimService {

    private final PaymentClaimRepository paymentClaimRepository;
    private final PayableRepository payableRepository;
    private final MemberRepository memberRepository;
    private final PaymentSettingsService paymentSettingsService;
    private final PayableService payableService;
    private final DarajaService darajaService;
    private final SmsMatchingService smsMatchingService;

    /**
     * Create a payment claim when user clicks "Pay" and selects a method.
     */
    @Transactional
    public PaymentClaimDto createClaim(Long memberId, Long tenantId, CreatePaymentClaimRequest request) {
        Payable payable = payableRepository.findByIdAndTenant_Id(request.getPayableId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Payable not found"));

        // Check if payable is still outstanding
        if (payable.getStatus() != PayableStatus.OUTSTANDING) {
            throw new RuntimeException("Payment is not outstanding. Current status: " + payable.getStatus());
        }

        // Check for existing active claim
        paymentClaimRepository.findActiveClaimByPayableId(payable.getId())
                .ifPresent(existing -> {
                    throw new RuntimeException("An active payment claim already exists for this payment");
                });

        Member payer = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        PaymentSettings settings = paymentSettingsService.getOrCreateSettings(tenantId);
        PaymentMethod method = PaymentMethod.valueOf(request.getPaymentMethod());

        // Validate payment method is enabled
        validatePaymentMethodEnabled(settings, method);

        BigDecimal amount = request.getAmount() != null ? request.getAmount() : payable.getAmount();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(settings.getClaimExpiryMinutes());

        PaymentClaim claim = PaymentClaim.builder()
                .payable(payable)
                .payer(payer)
                .amount(amount)
                .paymentMethod(method)
                .status(PaymentClaimStatus.PENDING_SMS)
                .expiresAt(expiresAt)
                .build();

        PaymentClaim saved = paymentClaimRepository.save(claim);

        // Update payable status
        payableService.markAwaitingConfirmation(payable.getId());

        log.info("Created payment claim {} for payable {} using method {}", 
                saved.getId(), payable.getId(), method);

        return toDto(saved);
    }

    /**
     * Initiate STK Push for a claim.
     */
    @Transactional
    public StkPushResponse initiateStkPush(Long claimId, String phoneNumber) {
        PaymentClaim claim = paymentClaimRepository.findByIdWithPayable(claimId)
                .orElseThrow(() -> new RuntimeException("Payment claim not found"));

        if (claim.getPaymentMethod() != PaymentMethod.STK_PUSH) {
            throw new RuntimeException("This claim is not for STK Push payment");
        }

        if (claim.getStatus() != PaymentClaimStatus.PENDING_SMS) {
            throw new RuntimeException("Claim is not in pending status");
        }

        return darajaService.initiateStkPush(claim, phoneNumber);
    }

    /**
     * Update claim with reference code from payer's SMS (In-App SMS flow).
     */
    @Transactional
    public PaymentClaimDto updateReference(Long claimId, UpdateClaimReferenceRequest request) {
        PaymentClaim claim = smsMatchingService.updateClaimWithReference(
                claimId,
                request.getReferenceCode(),
                request.getCapturedAt(),
                request.getPayerPhone());

        return toDto(claim);
    }

    /**
     * Get claim status for polling.
     */
    @Transactional(readOnly = true)
    public PaymentStatusResponse getClaimStatus(Long claimId) {
        PaymentClaim claim = paymentClaimRepository.findByIdWithPayable(claimId)
                .orElseThrow(() -> new RuntimeException("Payment claim not found"));

        return buildStatusResponse(claim);
    }

    /**
     * Manually verify a payment claim (for Manual method).
     */
    @Transactional
    public PaymentClaimDto verifyManually(Long claimId, Long verifierId, VerifyPaymentRequest request) {
        PaymentClaim claim = paymentClaimRepository.findByIdWithPayable(claimId)
                .orElseThrow(() -> new RuntimeException("Payment claim not found"));

        if (claim.getPaymentMethod() != PaymentMethod.MANUAL) {
            throw new RuntimeException("This claim does not use manual verification");
        }

        Member verifier = memberRepository.findById(verifierId)
                .orElseThrow(() -> new RuntimeException("Verifier not found"));

        claim.setReferenceCode(request.getReferenceCode());
        claim.setStatus(PaymentClaimStatus.MATCHED);
        claim.setMatchedAt(LocalDateTime.now());
        claim.setVerifiedBy(verifier);
        claim.setVerifiedAt(LocalDateTime.now());
        paymentClaimRepository.save(claim);

        // Mark payable as paid
        payableService.markAsPaid(claim.getPayable().getId());

        log.info("Payment claim {} manually verified by {}", claimId, verifierId);

        return toDto(claim);
    }

    /**
     * Reject a payment claim.
     */
    @Transactional
    public PaymentClaimDto rejectClaim(Long claimId, Long rejectorId, String reason) {
        PaymentClaim claim = paymentClaimRepository.findByIdWithPayable(claimId)
                .orElseThrow(() -> new RuntimeException("Payment claim not found"));

        claim.setStatus(PaymentClaimStatus.FAILED);
        claim.setRejectionReason(reason);
        claim.setVerifiedBy(memberRepository.findById(rejectorId).orElse(null));
        claim.setVerifiedAt(LocalDateTime.now());
        paymentClaimRepository.save(claim);

        // Revert payable to outstanding
        payableService.markExpired(claim.getPayable().getId());

        log.info("Payment claim {} rejected: {}", claimId, reason);

        return toDto(claim);
    }

    /**
     * Get claims awaiting manual verification.
     */
    @Transactional(readOnly = true)
    public List<PaymentClaimDto> getAwaitingManualVerification(Long tenantId) {
        return paymentClaimRepository.findAwaitingVerificationByTenantIdAndMethod(
                        tenantId, PaymentMethod.MANUAL)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get claims needing manual review (for treasurer dashboard).
     */
    @Transactional(readOnly = true)
    public Page<PaymentClaimDto> getManualReviewClaims(Long tenantId, Pageable pageable) {
        return paymentClaimRepository.findManualReviewByTenantId(tenantId, pageable)
                .map(this::toDto);
    }

    /**
     * Get payment history for a member.
     */
    @Transactional(readOnly = true)
    public Page<PaymentClaimDto> getPaymentHistory(Long memberId, Pageable pageable) {
        return paymentClaimRepository.findByPayerIdOrderByCreatedAtDesc(memberId, pageable)
                .map(this::toDto);
    }

    /**
     * Get dashboard statistics for treasurer.
     */
    @Transactional(readOnly = true)
    public PaymentDashboardDto getDashboard(Long tenantId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();

        long matchedToday = paymentClaimRepository.countByTenantIdAndStatus(
                tenantId, PaymentClaimStatus.MATCHED);
        long pendingReview = paymentClaimRepository.countByTenantIdAndStatus(
                tenantId, PaymentClaimStatus.MANUAL_REVIEW);

        // Get pending manual verification claims
        List<PaymentClaimDto> pendingManual = getAwaitingManualVerification(tenantId);

        return PaymentDashboardDto.builder()
                .matchedToday(matchedToday)
                .pendingReview(pendingReview)
                .pendingManualVerification(pendingManual)
                .build();
    }

    private void validatePaymentMethodEnabled(PaymentSettings settings, PaymentMethod method) {
        boolean enabled = switch (method) {
            case IN_APP_SMS -> settings.isInAppSmsEnabled();
            case STK_PUSH -> settings.isStkPushEnabled();
            case MANUAL -> settings.isManualEnabled();
        };

        if (!enabled) {
            throw new RuntimeException("Payment method " + method + " is not enabled");
        }
    }

    private PaymentStatusResponse buildStatusResponse(PaymentClaim claim) {
        LocalDateTime now = LocalDateTime.now();
        long secondsRemaining = claim.getExpiresAt() != null 
                ? ChronoUnit.SECONDS.between(now, claim.getExpiresAt())
                : 0;

        boolean isComplete = claim.getStatus() == PaymentClaimStatus.MATCHED
                || claim.getStatus() == PaymentClaimStatus.FAILED
                || claim.getStatus() == PaymentClaimStatus.EXPIRED;

        boolean isSuccess = claim.getStatus() == PaymentClaimStatus.MATCHED;

        String statusMessage = getStatusMessage(claim.getStatus(), claim.getPaymentMethod());

        return PaymentStatusResponse.builder()
                .claimId(claim.getId())
                .status(claim.getStatus().name())
                .paymentMethod(claim.getPaymentMethod().name())
                .amount(claim.getAmount())
                .payableId(claim.getPayable().getId())
                .payableType(claim.getPayable().getPayableType().name())
                .payableRefId(claim.getPayable().getPayableRefId())
                .description(claim.getPayable().getDescription())
                .matchedAt(claim.getMatchedAt())
                .mpesaReceipt(claim.getMpesaReceipt())
                .errorMessage(claim.getErrorMessage())
                .createdAt(claim.getCreatedAt())
                .expiresAt(claim.getExpiresAt())
                .secondsRemaining(Math.max(0, secondsRemaining))
                .statusMessage(statusMessage)
                .isComplete(isComplete)
                .isSuccess(isSuccess)
                .build();
    }

    private String getStatusMessage(PaymentClaimStatus status, PaymentMethod method) {
        return switch (status) {
            case PENDING_SMS -> method == PaymentMethod.STK_PUSH 
                    ? "Waiting for M-PESA prompt on your phone..."
                    : "Complete payment and capture the SMS";
            case PENDING_MATCH -> method == PaymentMethod.IN_APP_SMS
                    ? "Verifying payment with treasurer..."
                    : "Waiting for payment confirmation...";
            case PENDING_CALLBACK -> "Waiting for payment callback...";
            case MATCHED -> "Payment confirmed!";
            case EXPIRED -> "Payment claim expired";
            case MANUAL_REVIEW -> "Payment requires manual review";
            case FAILED -> "Payment failed";
        };
    }

    public PaymentClaimDto toDto(PaymentClaim claim) {
        PayableDto payableDto = payableService.toDto(claim.getPayable());

        return PaymentClaimDto.builder()
                .id(claim.getId())
                .payableId(claim.getPayable().getId())
                .payable(payableDto)
                .payerId(claim.getPayer().getId())
                .payerName(claim.getPayer().getName())
                .payerPhone(claim.getPayerPhone())
                .amount(claim.getAmount())
                .paymentMethod(claim.getPaymentMethod().name())
                .referenceCode(claim.getReferenceCode())
                .capturedAt(claim.getCapturedAt())
                .status(claim.getStatus().name())
                .matchedAt(claim.getMatchedAt())
                .mpesaReceipt(claim.getMpesaReceipt())
                .errorMessage(claim.getErrorMessage())
                .expiresAt(claim.getExpiresAt())
                .checkoutRequestId(claim.getCheckoutRequestId())
                .merchantRequestId(claim.getMerchantRequestId())
                .verifiedById(claim.getVerifiedBy() != null ? claim.getVerifiedBy().getId() : null)
                .verifiedByName(claim.getVerifiedBy() != null ? claim.getVerifiedBy().getName() : null)
                .verifiedAt(claim.getVerifiedAt())
                .rejectionReason(claim.getRejectionReason())
                .createdAt(claim.getCreatedAt())
                .updatedAt(claim.getUpdatedAt())
                .build();
    }
}
