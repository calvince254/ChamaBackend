package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PayoutMemberRepository payoutMemberRepository;
    private final TenantRepository tenantRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final InvestmentRepository investmentRepository;
    private final ContributionRepository contributionRepository;
    private final ShareCalculationService shareCalculationService;
    private final TransactionRepository transactionRepository;

    // ==================== LIQUIDITY CALCULATION ====================

    /**
     * Calculate the liquidity breakdown for a tenant.
     * Shows what's available as cash vs locked in investments/loans.
     */
    @Transactional(readOnly = true)
    public LiquidityDto calculateLiquidity(Long tenantId) {
        // Get total pool value from share calculation
        ShareCalculationDto shareCalc = shareCalculationService.calculateShares(tenantId);
        BigDecimal totalPoolValue = shareCalc.getTotalPoolValue();

        // Calculate outstanding loans (disbursed but not fully repaid)
        BigDecimal outstandingLoans = calculateOutstandingLoans(tenantId);

        // Calculate locked in investments (active investments at current value)
        BigDecimal lockedInInvestments = calculateLockedInvestments(tenantId);

        // Liquid cash = Total Pool - Outstanding Loans - Locked Investments
        // This is simplified; in reality, you'd track actual bank balance
        BigDecimal liquidCash = totalPoolValue
                .subtract(outstandingLoans)
                .subtract(lockedInInvestments);
        
        // Ensure liquid cash is not negative
        if (liquidCash.compareTo(BigDecimal.ZERO) < 0) {
            liquidCash = BigDecimal.ZERO;
        }

        // Get completed payouts total
        BigDecimal totalPaidOut = payoutRepository.sumCompletedPayoutsByTenantId(tenantId);
        if (totalPaidOut == null) totalPaidOut = BigDecimal.ZERO;

        return LiquidityDto.builder()
                .totalPoolValue(totalPoolValue)
                .liquidCash(liquidCash)
                .lockedInInvestments(lockedInInvestments)
                .outstandingLoans(outstandingLoans)
                .totalPaidOut(totalPaidOut)
                .hasActivePayout(payoutRepository.hasActivePayouts(tenantId))
                .calculatedAt(LocalDateTime.now().toString())
                .build();
    }

    /**
     * Calculate outstanding loans (principal + interest remaining)
     */
    private BigDecimal calculateOutstandingLoans(Long tenantId) {
        List<Loan> activeLoans = loanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.ACTIVE 
                        || l.getStatus() == Loan.LoanStatus.OVERDUE
                        || l.getStatus() == Loan.LoanStatus.APPROVED) // Approved but not yet disbursed
                .collect(Collectors.toList());

        BigDecimal outstanding = BigDecimal.ZERO;
        for (Loan loan : activeLoans) {
            BigDecimal totalAmount = loan.getTotalAmount() != null ? loan.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal totalPaid = loan.getTotalPaid() != null ? loan.getTotalPaid() : BigDecimal.ZERO;
            BigDecimal remaining = totalAmount.subtract(totalPaid);
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                outstanding = outstanding.add(remaining);
            }
        }
        return outstanding;
    }

    /**
     * Calculate locked in investments (active investments at current value)
     */
    private BigDecimal calculateLockedInvestments(Long tenantId) {
        List<Investment> activeInvestments = investmentRepository.findApprovedByTenantId(tenantId).stream()
                .filter(i -> i.getStatus() == Investment.InvestmentStatus.ACTIVE)
                .collect(Collectors.toList());

        BigDecimal locked = BigDecimal.ZERO;
        for (Investment inv : activeInvestments) {
            BigDecimal currentValue = inv.getCurrentValue() != null 
                    ? inv.getCurrentValue() 
                    : inv.getAmountInvested();
            if (currentValue != null) {
                locked = locked.add(currentValue);
            }
        }
        return locked;
    }

    // ==================== PAYOUT PREVIEW ====================

    /**
     * Preview a payout distribution without creating it.
     * Shows how much each member would receive based on current share percentages.
     */
    @Transactional(readOnly = true)
    public PayoutPreviewDto previewPayout(Long tenantId, Payout.PayoutType payoutType, BigDecimal amount, Long memberId) {
        ShareCalculationDto shareCalc = shareCalculationService.calculateShares(tenantId);
        LiquidityDto liquidity = calculateLiquidity(tenantId);

        if (!shareCalc.getIsConfigured()) {
            throw new RuntimeException("Share calculation is not configured");
        }

        List<MemberShareDto> memberShares = shareCalc.getMemberShares();
        List<PayoutMemberPreviewDto> memberPreviews = new ArrayList<>();

        BigDecimal payoutAmount = amount != null ? amount : liquidity.getLiquidCash();

        if (payoutType == Payout.PayoutType.INDIVIDUAL_WITHDRAWAL && memberId != null) {
            // Individual withdrawal - only one member
            MemberShareDto memberShare = memberShares.stream()
                    .filter(m -> m.getMemberId().equals(memberId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Member not found"));

            BigDecimal memberPayout = memberShare.getMemberValue();
            if (amount != null && amount.compareTo(memberPayout) < 0) {
                memberPayout = amount; // Partial withdrawal
            }

            memberPreviews.add(PayoutMemberPreviewDto.builder()
                    .memberId(memberShare.getMemberId())
                    .memberName(memberShare.getMemberName())
                    .sharePercentage(memberShare.getSharePercentage())
                    .contributionAmount(memberShare.getContributionAmount())
                    .payoutAmount(memberPayout)
                    .build());

            payoutAmount = memberPayout;
        } else {
            // Full or partial distribution to all members
            for (MemberShareDto memberShare : memberShares) {
                if (memberShare.getSharePercentage().compareTo(BigDecimal.ZERO) == 0) {
                    continue; // Skip members with 0% share
                }

                BigDecimal memberPayout = payoutAmount
                        .multiply(memberShare.getSharePercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                memberPreviews.add(PayoutMemberPreviewDto.builder()
                        .memberId(memberShare.getMemberId())
                        .memberName(memberShare.getMemberName())
                        .sharePercentage(memberShare.getSharePercentage())
                        .contributionAmount(memberShare.getContributionAmount())
                        .payoutAmount(memberPayout)
                        .build());
            }
        }

        // Sort by payout amount descending
        memberPreviews.sort((a, b) -> b.getPayoutAmount().compareTo(a.getPayoutAmount()));

        boolean canProceed = payoutAmount.compareTo(liquidity.getLiquidCash()) <= 0;
        String warning = null;
        if (!canProceed) {
            warning = "Payout amount exceeds available liquid cash. You may need to sell investments or recall loans.";
        }

        return PayoutPreviewDto.builder()
                .payoutType(payoutType.name())
                .totalAmount(payoutAmount)
                .memberCount(memberPreviews.size())
                .memberPreviews(memberPreviews)
                .liquidity(liquidity)
                .canProceed(canProceed)
                .warning(warning)
                .build();
    }

    // ==================== PAYOUT CRUD ====================

    /**
     * Create a new payout (draft status)
     */
    @Transactional
    public PayoutDto createPayout(CreatePayoutRequest request, Long tenantId, Long initiatorId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        Member initiator = memberRepository.findById(initiatorId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Check for active payouts
        if (payoutRepository.hasActivePayouts(tenantId)) {
            throw new RuntimeException("There is already an active payout in progress");
        }

        // Get share calculations
        ShareCalculationDto shareCalc = shareCalculationService.calculateShares(tenantId);
        if (!shareCalc.getIsConfigured()) {
            throw new RuntimeException("Share calculation is not configured");
        }

        LiquidityDto liquidity = calculateLiquidity(tenantId);

        // Create payout
        Payout payout = Payout.builder()
                .tenant(tenant)
                .payoutType(request.getPayoutType())
                .status(Payout.PayoutStatus.DRAFT)
                .description(request.getDescription())
                .totalPoolValue(shareCalc.getTotalPoolValue())
                .liquidCash(liquidity.getLiquidCash())
                .lockedInInvestments(liquidity.getLockedInInvestments())
                .outstandingLoans(liquidity.getOutstandingLoans())
                .payoutAmount(request.getAmount())
                .initiatedBy(initiator)
                .initiatedAt(LocalDateTime.now())
                .build();

        payout = payoutRepository.save(payout);

        // Create member payouts
        List<MemberShareDto> memberShares = shareCalc.getMemberShares();
        
        if (request.getPayoutType() == Payout.PayoutType.INDIVIDUAL_WITHDRAWAL && request.getMemberId() != null) {
            // Individual withdrawal
            MemberShareDto memberShare = memberShares.stream()
                    .filter(m -> m.getMemberId().equals(request.getMemberId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Member not found"));

            Member member = memberRepository.findById(memberShare.getMemberId())
                    .orElseThrow(() -> new RuntimeException("Member not found"));

            BigDecimal memberPayout = request.getAmount() != null 
                    ? request.getAmount() 
                    : memberShare.getMemberValue();

            PayoutMember pm = PayoutMember.builder()
                    .payout(payout)
                    .member(member)
                    .sharePercentage(memberShare.getSharePercentage())
                    .contributionAmount(memberShare.getContributionAmount())
                    .payoutAmount(memberPayout)
                    .status(PayoutMember.PayoutMemberStatus.PENDING)
                    .build();
            payout.addMemberPayout(pm);
        } else {
            // Distribution to all members
            for (MemberShareDto memberShare : memberShares) {
                if (memberShare.getSharePercentage().compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                Member member = memberRepository.findById(memberShare.getMemberId())
                        .orElseThrow(() -> new RuntimeException("Member not found: " + memberShare.getMemberId()));

                BigDecimal memberPayout = request.getAmount()
                        .multiply(memberShare.getSharePercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                PayoutMember pm = PayoutMember.builder()
                        .payout(payout)
                        .member(member)
                        .sharePercentage(memberShare.getSharePercentage())
                        .contributionAmount(memberShare.getContributionAmount())
                        .payoutAmount(memberPayout)
                        .status(PayoutMember.PayoutMemberStatus.PENDING)
                        .build();
                payout.addMemberPayout(pm);
            }
        }

        payout = payoutRepository.save(payout);
        return toDto(payout);
    }

    /**
     * Get all payouts for a tenant
     */
    @Transactional(readOnly = true)
    public List<PayoutDto> getPayouts(Long tenantId) {
        return payoutRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get a single payout with member details
     */
    @Transactional(readOnly = true)
    public PayoutDto getPayout(Long payoutId, Long tenantId) {
        Payout payout = payoutRepository.findByIdAndTenantId(payoutId, tenantId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));
        return toDto(payout);
    }

    // ==================== WORKFLOW ====================

    /**
     * Submit payout for approval
     */
    @Transactional
    public PayoutDto submitForApproval(Long payoutId, Long tenantId) {
        Payout payout = payoutRepository.findByIdAndTenantId(payoutId, tenantId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        if (payout.getStatus() != Payout.PayoutStatus.DRAFT) {
            throw new RuntimeException("Only draft payouts can be submitted for approval");
        }

        payout.setStatus(Payout.PayoutStatus.PENDING);
        payout = payoutRepository.save(payout);
        return toDto(payout);
    }

    /**
     * Approve a payout
     */
    @Transactional
    public PayoutDto approvePayout(Long payoutId, Long tenantId, Long approverId) {
        Payout payout = payoutRepository.findByIdAndTenantId(payoutId, tenantId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        if (payout.getStatus() != Payout.PayoutStatus.PENDING) {
            throw new RuntimeException("Only pending payouts can be approved");
        }

        Member approver = memberRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        payout.setStatus(Payout.PayoutStatus.APPROVED);
        payout.setApprovedBy(approver);
        payout.setApprovedAt(LocalDateTime.now());
        
        payout = payoutRepository.save(payout);
        return toDto(payout);
    }

    /**
     * Cancel a payout
     */
    @Transactional
    public PayoutDto cancelPayout(Long payoutId, Long tenantId, String reason) {
        Payout payout = payoutRepository.findByIdAndTenantId(payoutId, tenantId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        if (payout.getStatus() == Payout.PayoutStatus.COMPLETED) {
            throw new RuntimeException("Completed payouts cannot be cancelled");
        }

        payout.setStatus(Payout.PayoutStatus.CANCELLED);
        payout.setCancelledAt(LocalDateTime.now());
        payout.setCancellationReason(reason);

        // Cancel all member payouts
        for (PayoutMember pm : payout.getMemberPayouts()) {
            if (pm.getStatus() != PayoutMember.PayoutMemberStatus.PAID) {
                pm.setStatus(PayoutMember.PayoutMemberStatus.CANCELLED);
            }
        }

        payout = payoutRepository.save(payout);
        return toDto(payout);
    }

    /**
     * Mark a member's payout as paid
     */
    @Transactional
    public PayoutDto markMemberPaid(Long payoutId, Long memberId, Long tenantId, String paymentReference) {
        Payout payout = payoutRepository.findByIdAndTenantId(payoutId, tenantId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        if (payout.getStatus() != Payout.PayoutStatus.APPROVED) {
            throw new RuntimeException("Payout must be approved before marking members as paid");
        }

        PayoutMember pm = payout.getMemberPayouts().stream()
                .filter(m -> m.getMember().getId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Member not found in payout"));

        if (pm.getStatus() == PayoutMember.PayoutMemberStatus.PAID) {
            throw new RuntimeException("Member has already been paid");
        }

        pm.setStatus(PayoutMember.PayoutMemberStatus.PAID);
        pm.setPaidAt(LocalDateTime.now());
        pm.setPaymentReference(paymentReference);

        // Create transaction record for this payout
        createPayoutTransaction(payout, pm);

        // Check if all members are paid
        boolean allPaid = payout.getMemberPayouts().stream()
                .allMatch(m -> m.getStatus() == PayoutMember.PayoutMemberStatus.PAID 
                        || m.getStatus() == PayoutMember.PayoutMemberStatus.CANCELLED);

        if (allPaid) {
            payout.setStatus(Payout.PayoutStatus.COMPLETED);
            payout.setCompletedAt(LocalDateTime.now());
        }

        payout = payoutRepository.save(payout);
        return toDto(payout);
    }

    /**
     * Mark all members as paid at once
     */
    @Transactional
    public PayoutDto markAllPaid(Long payoutId, Long tenantId) {
        Payout payout = payoutRepository.findByIdAndTenantId(payoutId, tenantId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        if (payout.getStatus() != Payout.PayoutStatus.APPROVED) {
            throw new RuntimeException("Payout must be approved before marking members as paid");
        }

        LocalDateTime now = LocalDateTime.now();
        for (PayoutMember pm : payout.getMemberPayouts()) {
            if (pm.getStatus() == PayoutMember.PayoutMemberStatus.PENDING) {
                pm.setStatus(PayoutMember.PayoutMemberStatus.PAID);
                pm.setPaidAt(now);
                
                // Create transaction for each member
                createPayoutTransaction(payout, pm);
            }
        }

        payout.setStatus(Payout.PayoutStatus.COMPLETED);
        payout.setCompletedAt(now);

        payout = payoutRepository.save(payout);
        return toDto(payout);
    }

    /**
     * Create a transaction record for a payout to a member.
     * This records the money going OUT of the group to the member.
     */
    private void createPayoutTransaction(Payout payout, PayoutMember pm) {
        String description = String.format("%s payout to %s", 
                getPayoutTypeLabel(payout.getPayoutType()), 
                pm.getMember().getName());
        
        if (payout.getDescription() != null && !payout.getDescription().isEmpty()) {
            description += " - " + payout.getDescription();
        }

        Transaction transaction = Transaction.builder()
                .tenant(payout.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.PAYOUT)
                .description(description)
                .amount(pm.getPayoutAmount())
                .direction(Transaction.TransactionDirection.OUT)
                .member(pm.getMember())
                .referenceId(payout.getId())
                .referenceType("PAYOUT")
                .build();

        transactionRepository.save(transaction);
        log.info("Created payout transaction for member {} amount {}", 
                pm.getMember().getName(), pm.getPayoutAmount());
    }

    private String getPayoutTypeLabel(Payout.PayoutType type) {
        switch (type) {
            case FULL_DISTRIBUTION: return "Full distribution";
            case PARTIAL_DIVIDEND: return "Partial dividend";
            case INDIVIDUAL_WITHDRAWAL: return "Individual withdrawal";
            default: return type.name();
        }
    }

    // ==================== DTO CONVERSION ====================

    private PayoutDto toDto(Payout payout) {
        List<PayoutMemberDto> memberDtos = payout.getMemberPayouts().stream()
                .map(this::toMemberDto)
                .sorted((a, b) -> b.getPayoutAmount().compareTo(a.getPayoutAmount()))
                .collect(Collectors.toList());

        return PayoutDto.builder()
                .id(payout.getId())
                .payoutType(payout.getPayoutType().name())
                .status(payout.getStatus().name())
                .description(payout.getDescription())
                .totalPoolValue(payout.getTotalPoolValue())
                .liquidCash(payout.getLiquidCash())
                .lockedInInvestments(payout.getLockedInInvestments())
                .outstandingLoans(payout.getOutstandingLoans())
                .payoutAmount(payout.getPayoutAmount())
                .initiatedByName(payout.getInitiatedBy() != null ? payout.getInitiatedBy().getName() : null)
                .initiatedAt(payout.getInitiatedAt() != null ? payout.getInitiatedAt().toString() : null)
                .approvedByName(payout.getApprovedBy() != null ? payout.getApprovedBy().getName() : null)
                .approvedAt(payout.getApprovedAt() != null ? payout.getApprovedAt().toString() : null)
                .completedAt(payout.getCompletedAt() != null ? payout.getCompletedAt().toString() : null)
                .cancelledAt(payout.getCancelledAt() != null ? payout.getCancelledAt().toString() : null)
                .cancellationReason(payout.getCancellationReason())
                .memberCount(payout.getMemberCount())
                .paidCount((int) payout.getPaidCount())
                .memberPayouts(memberDtos)
                .createdAt(payout.getCreatedAt() != null ? payout.getCreatedAt().toString() : null)
                .build();
    }

    private PayoutMemberDto toMemberDto(PayoutMember pm) {
        return PayoutMemberDto.builder()
                .id(pm.getId())
                .memberId(pm.getMember().getId())
                .memberName(pm.getMember().getName())
                .memberNo(pm.getMember().getMemberNo())
                .sharePercentage(pm.getSharePercentage())
                .contributionAmount(pm.getContributionAmount())
                .payoutAmount(pm.getPayoutAmount())
                .status(pm.getStatus().name())
                .paidAt(pm.getPaidAt() != null ? pm.getPaidAt().toString() : null)
                .paymentReference(pm.getPaymentReference())
                .build();
    }
}
