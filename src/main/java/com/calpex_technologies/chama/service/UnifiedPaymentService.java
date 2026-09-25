package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Unified payment service that handles all payment types through a single flow.
 * Supports: REGISTRATION_FEE, FINE, MGR_FINE, PENALTY, etc.
 * 
 * Payment Methods:
 * - MANUAL: Member provides reference code, admin verifies
 * - STK_PUSH: M-PESA prompt, auto-confirms via callback
 * - IN_APP_SMS: Member captures SMS, treasurer matches
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedPaymentService {

    private final PayableRepository payableRepository;
    private final PaymentClaimRepository paymentClaimRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final FineRepository fineRepository;
    private final RegistrationFeePaymentRepository registrationFeePaymentRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentSettingsService paymentSettingsService;
    private final DarajaService darajaService;
    private final PenaltyRepository penaltyRepository;
    private final LoanPenaltyRepository loanPenaltyRepository;
    private final ContributionRepository contributionRepository;
    private final LoanRepository loanRepository;
    private final LoanPaymentRepository loanPaymentRepository;
    private final MerryGoRoundContributionRepository mgrContributionRepository;
    private final MerryGoRoundRoundRepository mgrRoundRepository;
    private final MerryGoRoundFineRepository mgrFineRepository;
    private final WelfareContributionRepository welfareContributionRepository;
    private final WelfareEventRepository welfareEventRepository;

    /**
     * Initiate payment for any payable type.
     * Creates Payable if needed, then creates PaymentClaim.
     */
    @Transactional
    public InitiatePaymentResponse initiatePayment(Long tenantId, Long payerMemberId, InitiatePaymentRequest request) {
        // Validate payment method is enabled
        PaymentSettings settings = paymentSettingsService.getOrCreateSettings(tenantId);
        PaymentMethod method = PaymentMethod.valueOf(request.getPaymentMethod());
        validatePaymentMethodEnabled(settings, method);

        // Get or create the payable
        Payable payable = getOrCreatePayable(tenantId, request);

        // Validate payable is still outstanding
        if (payable.getStatus() != PayableStatus.OUTSTANDING) {
            throw new RuntimeException("Payment is not outstanding. Current status: " + payable.getStatus());
        }

        // Check for existing active claim
        paymentClaimRepository.findActiveClaimByPayableId(payable.getId())
                .ifPresent(existing -> {
                    throw new RuntimeException("An active payment claim already exists for this payment");
                });

        // Get payer (who is making the payment)
        Member payer = memberRepository.findById(payerMemberId)
                .orElseThrow(() -> new RuntimeException("Payer member not found"));

        // Determine amount
        BigDecimal amount = request.getAmount() != null ? request.getAmount() : payable.getAmount();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(settings.getClaimExpiryMinutes());

        // Create payment claim
        PaymentClaim claim = PaymentClaim.builder()
                .payable(payable)
                .payer(payer)
                .amount(amount)
                .paymentMethod(method)
                .status(getInitialClaimStatus(method))
                .expiresAt(expiresAt)
                .build();

        // For MANUAL method, set the reference code immediately
        if (method == PaymentMethod.MANUAL && request.getReferenceCode() != null) {
            claim.setReferenceCode(request.getReferenceCode());
            claim.setCapturedAt(LocalDateTime.now());
            claim.setPayerPhone(payer.getPhone());
        }

        claim = paymentClaimRepository.save(claim);

        // Update payable status to awaiting confirmation
        payable.setStatus(PayableStatus.AWAITING_CONFIRMATION);
        payableRepository.save(payable);

        // For REGISTRATION_FEE, also update the RegistrationFeePayment record
        if (payable.getPayableType() == PayableType.REGISTRATION_FEE) {
            updateRegistrationFeeStatusOnInitiate(payable, claim);
        }

        log.info("Created payment claim {} for payable {} ({}) using method {}",
                claim.getId(), payable.getId(), payable.getPayableType(), method);

        // Handle method-specific actions
        InitiatePaymentResponse response = buildResponse(claim, payable, settings);

        // For STK_PUSH, initiate the prompt
        if (method == PaymentMethod.STK_PUSH) {
            String phoneNumber = request.getPhoneNumber() != null ? request.getPhoneNumber() : payer.getPhone();
            if (phoneNumber == null || phoneNumber.isBlank()) {
                throw new RuntimeException("Phone number is required for STK Push");
            }
            StkPushResponse stkResponse = darajaService.initiateStkPush(claim, phoneNumber);
            response.setCheckoutRequestId(stkResponse.getCheckoutRequestId());
            response.setMerchantRequestId(stkResponse.getMerchantRequestId());
            response.setStatus("STK_INITIATED");
            response.setStatusMessage("Check your phone for the M-PESA prompt");
        }

        return response;
    }

    /**
     * Get existing payable or create one on-the-fly for the payment type.
     */
    private Payable getOrCreatePayable(Long tenantId, InitiatePaymentRequest request) {
        // If payableId is provided, use it
        if (request.getPayableId() != null) {
            return payableRepository.findByIdAndTenant_Id(request.getPayableId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Payable not found: " + request.getPayableId()));
        }

        // Create payable based on payment type
        if (request.getPaymentType() == null) {
            throw new RuntimeException("Either payableId or paymentType must be provided");
        }

        PayableType payableType = PayableType.valueOf(request.getPaymentType());
        Long refId = request.getReferenceId();

        if (refId == null) {
            throw new RuntimeException("referenceId is required when payableId is not provided");
        }

        // Check for existing payable with OUTSTANDING or AWAITING_CONFIRMATION status
        var existingPayable = payableRepository.findByPayableTypeAndPayableRefIdAndTenant_IdAndStatusIn(
                payableType, refId, tenantId, 
                List.of(PayableStatus.OUTSTANDING, PayableStatus.AWAITING_CONFIRMATION));
        
        if (existingPayable.isPresent()) {
            Payable payable = existingPayable.get();
            // If already awaiting confirmation, don't allow a new payment attempt
            if (payable.getStatus() == PayableStatus.AWAITING_CONFIRMATION) {
                throw new RuntimeException("A payment for this item is already awaiting confirmation. Please wait for admin verification or cancel the existing claim.");
            }
            return payable;
        }

        // No existing payable found, create new one
        return createPayableForType(tenantId, payableType, refId, request);
    }

    /**
     * Create a new payable for the given payment type.
     */
    private Payable createPayableForType(Long tenantId, PayableType type, Long refId, InitiatePaymentRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        PaymentSettings settings = paymentSettingsService.getOrCreateSettings(tenantId);

        return switch (type) {
            case REGISTRATION_FEE -> createRegistrationFeePayable(tenant, refId, settings, request);
            case MEETING_FINE -> createFinePayable(tenant, refId, settings);
            case MGR_FINE -> createMgrFinePayable(tenant, refId, settings);
            case PENALTY -> createPenaltyPayable(tenant, refId, settings);
            case LOAN_PENALTY -> createLoanPenaltyPayable(tenant, refId, settings);
            case CONTRIBUTION -> createContributionPayable(tenant, refId, settings, request);
            case LOAN_REPAYMENT -> createLoanRepaymentPayable(tenant, refId, settings, request);
            case MGR_CONTRIBUTION -> createMgrContributionPayable(tenant, refId, settings);
            case WELFARE_DUE -> createWelfareContributionPayable(tenant, refId, settings);
            default -> throw new RuntimeException("Unsupported payment type for on-the-fly creation: " + type);
        };
    }

    /**
     * Create payable for registration fee.
     * refId = memberId (the member whose registration fee this is)
     */
    private Payable createRegistrationFeePayable(Tenant tenant, Long memberId, PaymentSettings settings, InitiatePaymentRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));

        // Get registration fee amount from tenant settings
        BigDecimal amount = request.getAmount() != null ? request.getAmount() : tenant.getRegistrationFee();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Registration fee amount is not configured");
        }

        // Check if already paid
        boolean alreadyPaid = registrationFeePaymentRepository.existsByTenantIdAndMemberIdAndStatus(
                tenant.getId(), memberId, RegistrationFeePayment.PaymentStatus.VERIFIED);
        if (alreadyPaid) {
            throw new RuntimeException("Registration fee already paid for this member");
        }

        String description = String.format("Registration Fee - %s", member.getName());

        Payable payable = Payable.builder()
                .tenant(tenant)
                .payableType(PayableType.REGISTRATION_FEE)
                .payableRefId(memberId)
                .member(member)
                .amount(amount)
                .recipientNumber(settings.getTreasurerPhone())
                .recipientName(settings.getTreasurerName())
                .status(PayableStatus.OUTSTANDING)
                .description(description)
                .build();

        return payableRepository.save(payable);
    }

    /**
     * Create payable for meeting fine.
     * refId = fineId
     */
    private Payable createFinePayable(Tenant tenant, Long fineId, PaymentSettings settings) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found: " + fineId));

        if (fine.getStatus() == Fine.FineStatus.PAID || fine.getStatus() == Fine.FineStatus.WAIVED) {
            throw new RuntimeException("Fine is already " + fine.getStatus());
        }

        // Calculate remaining balance
        BigDecimal paidAmount = fine.getPaidAmount() != null ? fine.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal balance = fine.getAmount().subtract(paidAmount);

        String description = String.format("Fine - %s (%s)", fine.getFineType().getName(), fine.getMember().getName());

        Payable payable = Payable.builder()
                .tenant(tenant)
                .payableType(PayableType.MEETING_FINE)
                .payableRefId(fineId)
                .member(fine.getMember())
                .amount(balance)
                .recipientNumber(settings.getTreasurerPhone())
                .recipientName(settings.getTreasurerName())
                .status(PayableStatus.OUTSTANDING)
                .description(description)
                .build();

        return payableRepository.save(payable);
    }

    /**
     * Create payable for merry-go-round fine.
     * refId = mgrFineId
     */
    private Payable createMgrFinePayable(Tenant tenant, Long mgrFineId, PaymentSettings settings) {
        // For now, throw unsupported - can be implemented when MGR fine entity is available
        throw new RuntimeException("MGR_FINE payment not yet implemented");
    }

    /**
     * Create payable for contribution penalty.
     * refId = penaltyId
     */
    private Payable createPenaltyPayable(Tenant tenant, Long penaltyId, PaymentSettings settings) {
        Penalty penalty = penaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new RuntimeException("Penalty not found: " + penaltyId));

        if (penalty.getStatus() == Penalty.PenaltyStatus.PAID || penalty.getStatus() == Penalty.PenaltyStatus.WAIVED) {
            throw new RuntimeException("Penalty is already " + penalty.getStatus());
        }

        // Calculate remaining balance
        BigDecimal paidAmount = penalty.getPaidAmount() != null ? penalty.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal balance = penalty.getAmount().subtract(paidAmount);

        String description = String.format("Penalty - %s (%s)", penalty.getReason(), penalty.getMember().getName());

        Payable payable = Payable.builder()
                .tenant(tenant)
                .payableType(PayableType.PENALTY)
                .payableRefId(penaltyId)
                .member(penalty.getMember())
                .amount(balance)
                .recipientNumber(settings.getTreasurerPhone())
                .recipientName(settings.getTreasurerName())
                .status(PayableStatus.OUTSTANDING)
                .description(description)
                .build();

        return payableRepository.save(payable);
    }

    /**
     * Create payable for loan penalty.
     * refId = loanPenaltyId
     */
    private Payable createLoanPenaltyPayable(Tenant tenant, Long loanPenaltyId, PaymentSettings settings) {
        LoanPenalty loanPenalty = loanPenaltyRepository.findById(loanPenaltyId)
                .orElseThrow(() -> new RuntimeException("Loan penalty not found: " + loanPenaltyId));

        if (loanPenalty.getStatus() == LoanPenalty.PenaltyStatus.PAID || loanPenalty.getStatus() == LoanPenalty.PenaltyStatus.WAIVED) {
            throw new RuntimeException("Loan penalty is already " + loanPenalty.getStatus());
        }

        // Calculate remaining balance
        BigDecimal balance = loanPenalty.getBalance();

        String description = String.format("Loan Penalty - %s (%s)", loanPenalty.getReason(), loanPenalty.getMember().getName());

        Payable payable = Payable.builder()
                .tenant(tenant)
                .payableType(PayableType.LOAN_PENALTY)
                .payableRefId(loanPenaltyId)
                .member(loanPenalty.getMember())
                .amount(balance)
                .recipientNumber(settings.getTreasurerPhone())
                .recipientName(settings.getTreasurerName())
                .status(PayableStatus.OUTSTANDING)
                .description(description)
                .build();

        return payableRepository.save(payable);
    }

    /**
     * Create payable for contribution.
     * refId = contributionId
     */
    private Payable createContributionPayable(Tenant tenant, Long contributionId, PaymentSettings settings, InitiatePaymentRequest request) {
        Contribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new RuntimeException("Contribution not found: " + contributionId));

        if (contribution.getStatus() == Contribution.ContributionStatus.PAID) {
            throw new RuntimeException("Contribution is already PAID");
        }

        // Calculate remaining balance
        BigDecimal paidAmount = contribution.getAmountPaid() != null ? contribution.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal balance = contribution.getAmountDue().subtract(paidAmount);
        
        // Allow partial payment if amount is specified
        BigDecimal paymentAmount = request.getAmount() != null ? request.getAmount() : balance;
        if (paymentAmount.compareTo(balance) > 0) {
            throw new RuntimeException("Payment amount cannot exceed balance of " + balance);
        }

        String description = String.format("Contribution - %s (%s, %s)", 
                contribution.getContributionType().getName(),
                contribution.getMember().getName(),
                contribution.getMonth().toString());

        Payable payable = Payable.builder()
                .tenant(tenant)
                .payableType(PayableType.CONTRIBUTION)
                .payableRefId(contributionId)
                .member(contribution.getMember())
                .amount(paymentAmount)
                .recipientNumber(settings.getTreasurerPhone())
                .recipientName(settings.getTreasurerName())
                .status(PayableStatus.OUTSTANDING)
                .description(description)
                .build();

        return payableRepository.save(payable);
    }

    /**
     * Create a payable for LOAN_REPAYMENT.
     * refId = loanId
     */
    private Payable createLoanRepaymentPayable(Tenant tenant, Long loanId, PaymentSettings settings, InitiatePaymentRequest request) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));

        if (loan.getStatus() == Loan.LoanStatus.CLEARED) {
            throw new RuntimeException("Loan is already CLEARED");
        }

        BigDecimal balance = loan.getBalanceOutstanding() != null ? loan.getBalanceOutstanding() : BigDecimal.ZERO;
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Loan has no outstanding balance");
        }

        // Allow partial payment if amount is specified
        BigDecimal paymentAmount = request.getAmount() != null ? request.getAmount() : balance;
        if (paymentAmount.compareTo(balance) > 0) {
            throw new RuntimeException("Payment amount cannot exceed balance of " + balance);
        }

        String description = String.format("Loan Repayment - %s (%s)", 
                loan.getLoanProduct().getName(),
                loan.getMember().getName());

        Payable payable = Payable.builder()
                .tenant(tenant)
                .payableType(PayableType.LOAN_REPAYMENT)
                .payableRefId(loanId)
                .member(loan.getMember())
                .amount(paymentAmount)
                .recipientNumber(settings.getTreasurerPhone())
                .recipientName(settings.getTreasurerName())
                .status(PayableStatus.OUTSTANDING)
                .description(description)
                .build();

        return payableRepository.save(payable);
    }

    /**
     * Create a payable for MGR_CONTRIBUTION.
     * refId = mgrContributionId
     */
    private Payable createMgrContributionPayable(Tenant tenant, Long mgrContributionId, PaymentSettings settings) {
        MerryGoRoundContribution contribution = mgrContributionRepository.findById(mgrContributionId)
                .orElseThrow(() -> new RuntimeException("MGR Contribution not found: " + mgrContributionId));

        if (contribution.getStatus() == MerryGoRoundContribution.ContributionStatus.PAID) {
            throw new RuntimeException("MGR Contribution is already PAID");
        }

        String description = String.format("MGR Contribution - %s (Round %d, %s)", 
                contribution.getMember().getName(),
                contribution.getRound().getPosition(),
                contribution.getCycle().getName());

        Payable payable = Payable.builder()
                .tenant(tenant)
                .payableType(PayableType.MGR_CONTRIBUTION)
                .payableRefId(mgrContributionId)
                .member(contribution.getMember())
                .amount(contribution.getAmount())
                .recipientNumber(settings.getTreasurerPhone())
                .recipientName(settings.getTreasurerName())
                .status(PayableStatus.OUTSTANDING)
                .description(description)
                .dueDate(contribution.getDueDate())
                .build();

        return payableRepository.save(payable);
    }

    /**
     * Create a payable for WELFARE_DUE.
     * refId = welfareContributionId
     */
    private Payable createWelfareContributionPayable(Tenant tenant, Long welfareContributionId, PaymentSettings settings) {
        WelfareContribution contribution = welfareContributionRepository.findById(welfareContributionId)
                .orElseThrow(() -> new RuntimeException("Welfare Contribution not found: " + welfareContributionId));

        if (contribution.getPaid() != null && contribution.getPaid()) {
            throw new RuntimeException("Welfare Contribution is already PAID");
        }

        // Calculate remaining balance (expected - paid)
        BigDecimal expected = contribution.getAmountExpected() != null ? contribution.getAmountExpected() : BigDecimal.ZERO;
        BigDecimal paid = contribution.getAmountPaid() != null ? contribution.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal balance = expected.subtract(paid);

        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("No outstanding balance for this welfare contribution");
        }

        WelfareEvent event = contribution.getWelfareEvent();
        String description = String.format("Welfare Contribution - %s (%s - %s)", 
                contribution.getMember().getName(),
                event.getEventType(),
                event.getMember() != null ? event.getMember().getName() : "Unknown");

        Payable payable = Payable.builder()
                .tenant(tenant)
                .payableType(PayableType.WELFARE_DUE)
                .payableRefId(welfareContributionId)
                .member(contribution.getMember())
                .amount(balance)
                .recipientNumber(settings.getTreasurerPhone())
                .recipientName(settings.getTreasurerName())
                .status(PayableStatus.OUTSTANDING)
                .description(description)
                .build();

        return payableRepository.save(payable);
    }

    /**
     * Verify a MANUAL payment claim (admin action).
     */
    @Transactional
    public InitiatePaymentResponse verifyManualPayment(Long tenantId, Long claimId, Long verifierMemberId) {
        PaymentClaim claim = paymentClaimRepository.findByIdWithPayable(claimId)
                .orElseThrow(() -> new RuntimeException("Payment claim not found"));

        if (!claim.getPayable().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Payment claim not found");
        }

        if (claim.getPaymentMethod() != PaymentMethod.MANUAL) {
            throw new RuntimeException("This claim does not use manual verification");
        }

        if (claim.getStatus() != PaymentClaimStatus.PENDING_SMS) {
            throw new RuntimeException("Claim is not pending verification. Status: " + claim.getStatus());
        }

        Member verifier = memberRepository.findById(verifierMemberId)
                .orElseThrow(() -> new RuntimeException("Verifier not found"));

        // Update claim status
        claim.setStatus(PaymentClaimStatus.MATCHED);
        claim.setMatchedAt(LocalDateTime.now());
        claim.setVerifiedBy(verifier);
        claim.setVerifiedAt(LocalDateTime.now());
        paymentClaimRepository.save(claim);

        // Update payable status
        Payable payable = claim.getPayable();
        payable.setStatus(PayableStatus.PAID);
        payableRepository.save(payable);

        // Update the source record and create transaction
        updateSourceRecordOnPayment(payable, claim);

        log.info("Payment claim {} verified by {}", claimId, verifierMemberId);

        return buildResponse(claim, payable, null);
    }

    /**
     * Confirm a payment by payableId (admin action).
     * This is a unified endpoint that works for ALL payment types and payment methods.
     * Finds the pending claim for the payable and confirms it.
     */
    @Transactional
    public InitiatePaymentResponse confirmPaymentByPayableId(Long tenantId, Long payableId, Long verifierMemberId) {
        Payable payable = payableRepository.findByIdAndTenant_Id(payableId, tenantId)
                .orElseThrow(() -> new RuntimeException("Payable not found"));

        if (payable.getStatus() != PayableStatus.AWAITING_CONFIRMATION) {
            throw new RuntimeException("Payable is not awaiting confirmation. Status: " + payable.getStatus());
        }

        // Find ANY pending claim for this payable (PENDING_SMS, PENDING_MATCH, or any non-terminal status)
        PaymentClaim claim = paymentClaimRepository.findByPayableIdAndStatusIn(
                payableId, List.of(PaymentClaimStatus.PENDING_SMS, PaymentClaimStatus.PENDING_MATCH, PaymentClaimStatus.PENDING_CALLBACK))
                .orElse(null);
        
        // If no claim found with pending status, try to find any claim for this payable
        if (claim == null) {
            claim = paymentClaimRepository.findActiveClaimByPayableId(payableId).orElse(null);
        }
        
        if (claim == null) {
            // No claim exists - create one and immediately confirm it (for cases where Payable was set to AWAITING_CONFIRMATION manually)
            log.warn("No payment claim found for payable {}. Creating and confirming directly.", payableId);
            
            Member verifier = memberRepository.findById(verifierMemberId)
                    .orElseThrow(() -> new RuntimeException("Verifier not found"));
            
            // Create a claim record for audit purposes
            claim = PaymentClaim.builder()
                    .payable(payable)
                    .payer(payable.getMember())
                    .amount(payable.getAmount())
                    .paymentMethod(PaymentMethod.MANUAL)
                    .status(PaymentClaimStatus.MATCHED)
                    .matchedAt(LocalDateTime.now())
                    .verifiedBy(verifier)
                    .verifiedAt(LocalDateTime.now())
                    .build();
            claim = paymentClaimRepository.save(claim);
            
            // Update payable status
            payable.setStatus(PayableStatus.PAID);
            payableRepository.save(payable);

            // Update the source record and create transaction
            updateSourceRecordOnPayment(payable, claim);

            log.info("Payment for payable {} confirmed directly by {}", payableId, verifierMemberId);
            return buildResponse(claim, payable, null);
        }

        Member verifier = memberRepository.findById(verifierMemberId)
                .orElseThrow(() -> new RuntimeException("Verifier not found"));

        // Update claim status
        claim.setStatus(PaymentClaimStatus.MATCHED);
        claim.setMatchedAt(LocalDateTime.now());
        claim.setVerifiedBy(verifier);
        claim.setVerifiedAt(LocalDateTime.now());
        paymentClaimRepository.save(claim);

        // Update payable status
        payable.setStatus(PayableStatus.PAID);
        payableRepository.save(payable);

        // Update the source record and create transaction
        updateSourceRecordOnPayment(payable, claim);

        log.info("Payment for payable {} confirmed by {}", payableId, verifierMemberId);

        return buildResponse(claim, payable, null);
    }

    /**
     * Reject a payment claim (admin action).
     */
    @Transactional
    public InitiatePaymentResponse rejectPayment(Long tenantId, Long claimId, Long rejectorMemberId, String reason) {
        PaymentClaim claim = paymentClaimRepository.findByIdWithPayable(claimId)
                .orElseThrow(() -> new RuntimeException("Payment claim not found"));

        if (!claim.getPayable().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Payment claim not found");
        }

        Member rejector = memberRepository.findById(rejectorMemberId).orElse(null);

        // Update claim
        claim.setStatus(PaymentClaimStatus.FAILED);
        claim.setRejectionReason(reason);
        claim.setVerifiedBy(rejector);
        claim.setVerifiedAt(LocalDateTime.now());
        paymentClaimRepository.save(claim);

        // Revert payable to outstanding
        Payable payable = claim.getPayable();
        payable.setStatus(PayableStatus.OUTSTANDING);
        payableRepository.save(payable);

        log.info("Payment claim {} rejected: {}", claimId, reason);

        return buildResponse(claim, payable, null);
    }

    /**
     * Reject a payment by payableId (admin action).
     * This is a unified endpoint that works for ALL payment types.
     */
    @Transactional
    public InitiatePaymentResponse rejectPaymentByPayableId(Long tenantId, Long payableId, Long rejectorMemberId, String reason) {
        Payable payable = payableRepository.findByIdAndTenant_Id(payableId, tenantId)
                .orElseThrow(() -> new RuntimeException("Payable not found"));

        if (payable.getStatus() != PayableStatus.AWAITING_CONFIRMATION) {
            throw new RuntimeException("Payable is not awaiting confirmation. Status: " + payable.getStatus());
        }

        // Find the pending claim for this payable
        PaymentClaim claim = paymentClaimRepository.findByPayableIdAndStatusIn(
                payableId, List.of(PaymentClaimStatus.PENDING_SMS, PaymentClaimStatus.PENDING_MATCH))
                .orElseThrow(() -> new RuntimeException("No pending payment claim found for this payable"));

        Member rejector = memberRepository.findById(rejectorMemberId).orElse(null);

        // Update claim
        claim.setStatus(PaymentClaimStatus.FAILED);
        claim.setRejectionReason(reason);
        claim.setVerifiedBy(rejector);
        claim.setVerifiedAt(LocalDateTime.now());
        paymentClaimRepository.save(claim);

        // Revert payable to outstanding
        payable.setStatus(PayableStatus.OUTSTANDING);
        payableRepository.save(payable);

        log.info("Payment for payable {} rejected by {}: {}", payableId, rejectorMemberId, reason);

        return buildResponse(claim, payable, null);
    }

    /**
     * Update the source record when payment is confirmed.
     */
    private void updateSourceRecordOnPayment(Payable payable, PaymentClaim claim) {
        switch (payable.getPayableType()) {
            case REGISTRATION_FEE -> updateRegistrationFeeOnPayment(payable, claim);
            case MEETING_FINE -> updateFineOnPayment(payable, claim);
            case PENALTY -> updatePenaltyOnPayment(payable, claim);
            case LOAN_PENALTY -> updateLoanPenaltyOnPayment(payable, claim);
            case CONTRIBUTION -> updateContributionOnPayment(payable, claim);
            case LOAN_REPAYMENT -> updateLoanRepaymentOnPayment(payable, claim);
            case MGR_CONTRIBUTION -> updateMgrContributionOnPayment(payable, claim);
            case MGR_FINE -> updateMgrFineOnPayment(payable, claim);
            case WELFARE_DUE -> updateWelfareContributionOnPayment(payable, claim);
            default -> log.info("No source record update for type: {}", payable.getPayableType());
        }
    }

    /**
     * Update registration fee record when payment is initiated (before confirmation).
     * Sets status to AWAITING_VERIFICATION so frontend can display correct status.
     */
    private void updateRegistrationFeeStatusOnInitiate(Payable payable, PaymentClaim claim) {
        Long memberId = payable.getPayableRefId();
        Long tenantId = payable.getTenant().getId();

        // Find or create the registration fee payment record
        RegistrationFeePayment regFeePayment = registrationFeePaymentRepository
                .findByTenantIdAndMemberId(tenantId, memberId)
                .orElseGet(() -> {
                    Tenant tenant = payable.getTenant();
                    Member member = payable.getMember();
                    return RegistrationFeePayment.builder()
                            .tenant(tenant)
                            .member(member)
                            .amount(payable.getAmount())
                            .build();
                });

        regFeePayment.setReferenceNumber(claim.getReferenceCode());
        regFeePayment.setStatus(RegistrationFeePayment.PaymentStatus.AWAITING_VERIFICATION);
        regFeePayment.setPaidAt(LocalDateTime.now());
        registrationFeePaymentRepository.save(regFeePayment);

        log.info("Registration fee payment initiated for member {}, status set to AWAITING_VERIFICATION", memberId);
    }

    /**
     * Update registration fee record when payment is confirmed.
     */
    private void updateRegistrationFeeOnPayment(Payable payable, PaymentClaim claim) {
        Long memberId = payable.getPayableRefId();
        Long tenantId = payable.getTenant().getId();

        // Find or create the registration fee payment record
        RegistrationFeePayment regFeePayment = registrationFeePaymentRepository
                .findByTenantIdAndMemberId(tenantId, memberId)
                .orElseGet(() -> {
                    Tenant tenant = payable.getTenant();
                    Member member = payable.getMember();
                    return RegistrationFeePayment.builder()
                            .tenant(tenant)
                            .member(member)
                            .amount(payable.getAmount())
                            .build();
                });

        regFeePayment.setReferenceNumber(claim.getReferenceCode());
        regFeePayment.setStatus(RegistrationFeePayment.PaymentStatus.VERIFIED);
        regFeePayment.setPaidAt(claim.getCapturedAt() != null ? claim.getCapturedAt() : LocalDateTime.now());
        regFeePayment.setVerifiedAt(claim.getVerifiedAt());
        regFeePayment.setVerifiedBy(claim.getVerifiedBy());
        registrationFeePaymentRepository.save(regFeePayment);

        // Create GL transaction
        String description = String.format("Registration fee - %s (Ref: %s)",
                payable.getMember().getName(), claim.getReferenceCode());
        Transaction transaction = Transaction.builder()
                .tenant(payable.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.REGISTRATION_FEE)
                .description(description)
                .amount(payable.getAmount())
                .direction(Transaction.TransactionDirection.IN)
                .member(payable.getMember())
                .referenceId(regFeePayment.getId())
                .referenceType("REGISTRATION_FEE")
                .build();
        transactionRepository.save(transaction);

        log.info("Registration fee payment confirmed for member {}", memberId);
    }

    /**
     * Update fine record when payment is confirmed.
     */
    private void updateFineOnPayment(Payable payable, PaymentClaim claim) {
        Long fineId = payable.getPayableRefId();

        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found: " + fineId));

        // Add to paid amount
        BigDecimal currentPaid = fine.getPaidAmount() != null ? fine.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = currentPaid.add(claim.getAmount());
        fine.setPaidAmount(newPaidAmount);
        fine.setPaidAt(LocalDateTime.now());

        // Update status
        if (newPaidAmount.compareTo(fine.getAmount()) >= 0) {
            fine.setStatus(Fine.FineStatus.PAID);
        } else {
            fine.setStatus(Fine.FineStatus.PARTIAL);
        }
        fineRepository.save(fine);

        // Create GL transaction
        String description = String.format("Fine payment - %s (%s, Ref: %s)",
                fine.getMember().getName(), fine.getFineType().getName(), claim.getReferenceCode());
        Transaction transaction = Transaction.builder()
                .tenant(payable.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.FINE)
                .description(description)
                .amount(claim.getAmount())
                .direction(Transaction.TransactionDirection.IN)
                .member(fine.getMember())
                .referenceId(fine.getId())
                .referenceType("FINE")
                .build();
        transactionRepository.save(transaction);

        log.info("Fine {} payment confirmed, new status: {}", fineId, fine.getStatus());
    }

    /**
     * Update contribution penalty record when payment is confirmed.
     */
    private void updatePenaltyOnPayment(Payable payable, PaymentClaim claim) {
        Long penaltyId = payable.getPayableRefId();

        Penalty penalty = penaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new RuntimeException("Penalty not found: " + penaltyId));

        // Add to paid amount
        BigDecimal currentPaid = penalty.getPaidAmount() != null ? penalty.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = currentPaid.add(claim.getAmount());
        penalty.setPaidAmount(newPaidAmount);
        penalty.setPaidAt(LocalDateTime.now());

        // Update status
        if (newPaidAmount.compareTo(penalty.getAmount()) >= 0) {
            penalty.setStatus(Penalty.PenaltyStatus.PAID);
        } else {
            penalty.setStatus(Penalty.PenaltyStatus.PARTIAL);
        }
        penaltyRepository.save(penalty);

        // Create GL transaction
        String description = String.format("Penalty payment - %s (%s, Ref: %s)",
                penalty.getMember().getName(), penalty.getReason(), claim.getReferenceCode());
        Transaction transaction = Transaction.builder()
                .tenant(payable.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.PENALTY)
                .description(description)
                .amount(claim.getAmount())
                .direction(Transaction.TransactionDirection.IN)
                .member(penalty.getMember())
                .referenceId(penalty.getId())
                .referenceType("PENALTY")
                .build();
        transactionRepository.save(transaction);

        log.info("Penalty {} payment confirmed, new status: {}", penaltyId, penalty.getStatus());
    }

    /**
     * Update loan penalty record when payment is confirmed.
     */
    private void updateLoanPenaltyOnPayment(Payable payable, PaymentClaim claim) {
        Long loanPenaltyId = payable.getPayableRefId();

        LoanPenalty loanPenalty = loanPenaltyRepository.findById(loanPenaltyId)
                .orElseThrow(() -> new RuntimeException("Loan penalty not found: " + loanPenaltyId));

        // Add to paid amount
        BigDecimal currentPaid = loanPenalty.getPaidAmount() != null ? loanPenalty.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = currentPaid.add(claim.getAmount());
        loanPenalty.setPaidAmount(newPaidAmount);
        loanPenalty.setPaidAt(LocalDateTime.now());

        // Update status
        if (newPaidAmount.compareTo(loanPenalty.getAmount()) >= 0) {
            loanPenalty.setStatus(LoanPenalty.PenaltyStatus.PAID);
        } else {
            loanPenalty.setStatus(LoanPenalty.PenaltyStatus.PARTIAL);
        }
        loanPenaltyRepository.save(loanPenalty);

        // Create GL transaction
        String description = String.format("Loan penalty payment - %s (%s, Ref: %s)",
                loanPenalty.getMember().getName(), loanPenalty.getReason(), claim.getReferenceCode());
        Transaction transaction = Transaction.builder()
                .tenant(payable.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.LOAN_PENALTY)
                .description(description)
                .amount(claim.getAmount())
                .direction(Transaction.TransactionDirection.IN)
                .member(loanPenalty.getMember())
                .referenceId(loanPenalty.getId())
                .referenceType("LOAN_PENALTY")
                .build();
        transactionRepository.save(transaction);

        log.info("Loan penalty {} payment confirmed, new status: {}", loanPenaltyId, loanPenalty.getStatus());
    }

    /**
     * Update contribution record when payment is confirmed.
     */
    private void updateContributionOnPayment(Payable payable, PaymentClaim claim) {
        Long contributionId = payable.getPayableRefId();

        Contribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new RuntimeException("Contribution not found: " + contributionId));

        // Add to paid amount
        BigDecimal currentPaid = contribution.getAmountPaid() != null ? contribution.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = currentPaid.add(claim.getAmount());
        contribution.setAmountPaid(newPaidAmount);
        contribution.setPaidOn(LocalDate.now());

        // Update status
        if (newPaidAmount.compareTo(contribution.getAmountDue()) >= 0) {
            contribution.setStatus(Contribution.ContributionStatus.PAID);
        } else if (newPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
            contribution.setStatus(Contribution.ContributionStatus.PARTIAL);
        }
        contributionRepository.save(contribution);

        // Update member's total savings
        Member member = contribution.getMember();
        BigDecimal totalSavings = contributionRepository.sumAmountPaidByTenantId(member.getTenant().getId());
        member.setTotalSavings(totalSavings != null ? totalSavings : BigDecimal.ZERO);

        // Create GL transaction
        String description = String.format("Contribution payment - %s (%s, %s, Ref: %s)",
                contribution.getMember().getName(), 
                contribution.getContributionType().getName(),
                contribution.getMonth().toString(),
                claim.getReferenceCode());
        Transaction transaction = Transaction.builder()
                .tenant(payable.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.CONTRIBUTION)
                .description(description)
                .amount(claim.getAmount())
                .direction(Transaction.TransactionDirection.IN)
                .member(contribution.getMember())
                .referenceId(contribution.getId())
                .referenceType("CONTRIBUTION")
                .build();
        transactionRepository.save(transaction);

        log.info("Contribution {} payment confirmed, new status: {}", contributionId, contribution.getStatus());
    }

    /**
     * Update loan record when repayment is confirmed.
     */
    private void updateLoanRepaymentOnPayment(Payable payable, PaymentClaim claim) {
        Long loanId = payable.getPayableRefId();

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));

        // Reduce outstanding balance
        BigDecimal currentBalance = loan.getBalanceOutstanding() != null ? loan.getBalanceOutstanding() : BigDecimal.ZERO;
        BigDecimal newBalance = currentBalance.subtract(claim.getAmount());
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }
        loan.setBalanceOutstanding(newBalance);

        // Update total paid
        BigDecimal totalPaid = loan.getTotalPaid() != null ? loan.getTotalPaid() : BigDecimal.ZERO;
        loan.setTotalPaid(totalPaid.add(claim.getAmount()));

        // Update status
        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(Loan.LoanStatus.CLEARED);
            loan.setNextDueDate(null);
            loan.setClearedDate(LocalDate.now());
        }
        loanRepository.save(loan);

        // Update member outstanding loan balance
        Member member = loan.getMember();
        BigDecimal totalLoans = loanRepository.sumOutstandingByMemberId(member.getId());
        member.setOutstandingLoan(totalLoans != null ? totalLoans : BigDecimal.ZERO);

        // Create GL transaction
        String description = String.format("Loan repayment - %s (%s, Ref: %s)",
                loan.getMember().getName(),
                loan.getLoanProduct().getName(),
                claim.getReferenceCode());
        Transaction transaction = Transaction.builder()
                .tenant(payable.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.LOAN_REPAYMENT)
                .description(description)
                .amount(claim.getAmount())
                .direction(Transaction.TransactionDirection.IN)
                .member(loan.getMember())
                .referenceId(loan.getId())
                .referenceType("LOAN")
                .build();
        transactionRepository.save(transaction);

        // Create LoanPayment record for payment history
        LoanPayment loanPayment = LoanPayment.builder()
                .tenant(payable.getTenant())
                .loan(loan)
                .member(loan.getMember())
                .amount(claim.getAmount())
                .paymentDate(LocalDate.now())
                .paymentMethod(claim.getPaymentMethod() != null ? claim.getPaymentMethod().name() : null)
                .transactionReference(claim.getReferenceCode())
                .status(LoanPayment.PaymentStatus.VERIFIED)
                .verifiedAt(LocalDateTime.now())
                .build();
        loanPaymentRepository.save(loanPayment);

        log.info("Loan {} repayment confirmed, new balance: {}", loanId, newBalance);
    }

    /**
     * Update MGR contribution record when payment is confirmed.
     */
    private void updateMgrContributionOnPayment(Payable payable, PaymentClaim claim) {
        Long mgrContributionId = payable.getPayableRefId();

        MerryGoRoundContribution contribution = mgrContributionRepository.findById(mgrContributionId)
                .orElseThrow(() -> new RuntimeException("MGR Contribution not found: " + mgrContributionId));

        // Mark as paid
        contribution.setStatus(MerryGoRoundContribution.ContributionStatus.PAID);
        contribution.setPaidDate(LocalDate.now());
        contribution.setPaymentReference(claim.getReferenceCode());
        mgrContributionRepository.save(contribution);

        // Update round collected amount
        MerryGoRoundRound round = contribution.getRound();
        BigDecimal currentCollected = round.getCollectedAmount() != null ? round.getCollectedAmount() : BigDecimal.ZERO;
        round.setCollectedAmount(currentCollected.add(claim.getAmount()));
        mgrRoundRepository.save(round);

        // NOTE: MGR contributions are NOT recorded in the finance book
        // because MGR is a revolving fund where money goes back to members.
        // Only MGR fines are recorded as they represent actual income to the chama.

        log.info("MGR Contribution {} payment confirmed", mgrContributionId);
    }

    /**
     * Update MGR fine record when payment is confirmed.
     */
    private void updateMgrFineOnPayment(Payable payable, PaymentClaim claim) {
        Long mgrFineId = payable.getPayableRefId();

        MerryGoRoundFine fine = mgrFineRepository.findById(mgrFineId)
                .orElseThrow(() -> new RuntimeException("MGR Fine not found: " + mgrFineId));

        // Mark as paid
        fine.setStatus(MerryGoRoundFine.FineStatus.PAID);
        fine.setPaidDate(LocalDate.now());
        mgrFineRepository.save(fine);

        // Create GL transaction - MGR fines ARE recorded as FINE type
        String description = String.format("MGR Fine Payment - %s (%s, Ref: %s)",
                fine.getMember().getName(),
                fine.getReason(),
                claim.getReferenceCode() != null ? claim.getReferenceCode() : "N/A");
        Transaction transaction = Transaction.builder()
                .tenant(payable.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.FINE)
                .description(description)
                .amount(claim.getAmount())
                .direction(Transaction.TransactionDirection.IN)
                .member(fine.getMember())
                .referenceId(fine.getId())
                .referenceType("MGR_FINE")
                .build();
        transactionRepository.save(transaction);

        log.info("MGR Fine {} payment confirmed for member {}", mgrFineId, fine.getMember().getName());
    }

    /**
     * Update welfare contribution record when payment is confirmed.
     */
    private void updateWelfareContributionOnPayment(Payable payable, PaymentClaim claim) {
        Long welfareContributionId = payable.getPayableRefId();

        WelfareContribution contribution = welfareContributionRepository.findById(welfareContributionId)
                .orElseThrow(() -> new RuntimeException("Welfare Contribution not found: " + welfareContributionId));

        // Add to paid amount
        BigDecimal currentPaid = contribution.getAmountPaid() != null ? contribution.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = currentPaid.add(claim.getAmount());
        contribution.setAmountPaid(newPaidAmount);
        contribution.setPaymentReference(claim.getReferenceCode());

        // Check if fully paid
        BigDecimal expected = contribution.getAmountExpected() != null ? contribution.getAmountExpected() : BigDecimal.ZERO;
        if (newPaidAmount.compareTo(expected) >= 0) {
            contribution.setPaid(true);
            contribution.setPaidDate(LocalDateTime.now());
        }
        welfareContributionRepository.save(contribution);

        // Update welfare event totals
        WelfareEvent event = contribution.getWelfareEvent();
        BigDecimal totalCollected = welfareContributionRepository.sumPaidByEventId(event.getId());
        event.setTotalCollected(totalCollected != null ? totalCollected : BigDecimal.ZERO);
        welfareEventRepository.save(event);

        // Create GL transaction
        String description = String.format("Welfare Contribution - %s (%s, Ref: %s)",
                contribution.getMember().getName(),
                event.getEventType(),
                claim.getReferenceCode());
        Transaction transaction = Transaction.builder()
                .tenant(payable.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.WELFARE)
                .description(description)
                .amount(claim.getAmount())
                .direction(Transaction.TransactionDirection.IN)
                .member(contribution.getMember())
                .referenceId(contribution.getId())
                .referenceType("WELFARE_CONTRIBUTION")
                .build();
        transactionRepository.save(transaction);

        log.info("Welfare Contribution {} payment confirmed, paid: {}", welfareContributionId, contribution.getPaid());
    }

    private PaymentClaimStatus getInitialClaimStatus(PaymentMethod method) {
        return switch (method) {
            case MANUAL -> PaymentClaimStatus.PENDING_SMS;  // Awaiting admin verification
            case STK_PUSH -> PaymentClaimStatus.PENDING_SMS;  // Awaiting callback
            case IN_APP_SMS -> PaymentClaimStatus.PENDING_SMS;  // Awaiting SMS capture
        };
    }

    private void validatePaymentMethodEnabled(PaymentSettings settings, PaymentMethod method) {
        boolean enabled = switch (method) {
            case IN_APP_SMS -> settings.isInAppSmsEnabled();
            case STK_PUSH -> settings.isStkPushEnabled();
            case MANUAL -> settings.isManualEnabled();
        };

        if (!enabled) {
            throw new RuntimeException("Payment method " + method + " is not enabled for this chama");
        }
    }

    private InitiatePaymentResponse buildResponse(PaymentClaim claim, Payable payable, PaymentSettings settings) {
        // awaitingVerification should be true for MANUAL and IN_APP_SMS methods when status is PENDING_SMS
        // This allows the frontend to close the dialog and show "Awaiting Confirmation" badge
        // STK_PUSH will have its status overwritten to STK_INITIATED after this method
        boolean awaitingVerification = (claim.getPaymentMethod() == PaymentMethod.MANUAL 
                || claim.getPaymentMethod() == PaymentMethod.IN_APP_SMS)
                && claim.getStatus() == PaymentClaimStatus.PENDING_SMS;
        
        long expiresInSeconds = 0;
        if (claim.getExpiresAt() != null) {
            expiresInSeconds = Math.max(0, ChronoUnit.SECONDS.between(LocalDateTime.now(), claim.getExpiresAt()));
        }

        String statusMessage = getStatusMessage(claim);

        return InitiatePaymentResponse.builder()
                .payableId(payable.getId())
                .claimId(claim.getId())
                .paymentMethod(claim.getPaymentMethod().name())
                .status(claim.getStatus().name())
                .statusMessage(statusMessage)
                .amount(claim.getAmount())
                .referenceCode(claim.getReferenceCode())
                .awaitingVerification(awaitingVerification)
                .expiresInSeconds(expiresInSeconds)
                .build();
    }

    private String getStatusMessage(PaymentClaim claim) {
        return switch (claim.getStatus()) {
            case PENDING_SMS -> claim.getPaymentMethod() == PaymentMethod.MANUAL
                    ? "Payment submitted. Awaiting admin verification."
                    : claim.getPaymentMethod() == PaymentMethod.STK_PUSH
                    ? "Check your phone for the M-PESA prompt."
                    : "Payment initiated. Complete the payment and an admin will verify it.";
            case PENDING_MATCH -> "Verifying payment...";
            case PENDING_CALLBACK -> "Waiting for payment callback...";
            case MATCHED -> "Payment confirmed!";
            case EXPIRED -> "Payment claim expired.";
            case MANUAL_REVIEW -> "Payment requires manual review.";
            case FAILED -> "Payment failed.";
        };
    }
}
