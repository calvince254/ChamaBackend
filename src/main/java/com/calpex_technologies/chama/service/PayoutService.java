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
     * Liquid Cash = Total Pool Value - Locked Investments - Outstanding Loans
     * This uses the same calculation as the share calculation service for consistency.
     */
    @Transactional(readOnly = true)
    public LiquidityDto calculateLiquidity(Long tenantId) {
        // Get total pool value from share calculation (uses the configured rules in settings)
        ShareCalculationDto shareCalc = shareCalculationService.calculateShares(tenantId);
        BigDecimal totalPoolValue = shareCalc.getTotalPoolValue();

        // Calculate outstanding loans (disbursed but not fully repaid)
        BigDecimal outstandingLoans = calculateOutstandingLoans(tenantId);

        // Calculate locked in investments (active investments at current value)
        BigDecimal lockedInInvestments = calculateLockedInvestments(tenantId);

        // Liquid cash = Total Pool Value - Locked Investments - Outstanding Loans
        // Outstanding loans represent money that was disbursed from the pool and hasn't been returned yet
        // Locked investments represent money that was invested and can't be withdrawn yet
        BigDecimal liquidCash = totalPoolValue
                .subtract(lockedInInvestments)
                .subtract(outstandingLoans);
        
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

    /**
     * Calculate how much a member is liable for as a guarantor.
     * This is the total outstanding balance of loans they have guaranteed
     * that are still active/overdue and not fully paid.
     */
    private BigDecimal calculateGuarantorLiability(Long memberId, Long tenantId) {
        // Find all active/overdue loans with guarantors eagerly fetched
        List<Loan> activeLoans = loanRepository.findActiveLoansWithGuarantors(tenantId).stream()
                .filter(l -> l.getGuarantors() != null && 
                        l.getGuarantors().stream().anyMatch(g -> g.getId().equals(memberId)))
                .collect(Collectors.toList());

        BigDecimal totalLiability = BigDecimal.ZERO;
        for (Loan loan : activeLoans) {
            BigDecimal outstanding = loan.getBalanceOutstanding() != null 
                    ? loan.getBalanceOutstanding() : BigDecimal.ZERO;
            
            // Divide liability among all guarantors
            int guarantorCount = loan.getGuarantors() != null ? loan.getGuarantors().size() : 1;
            if (guarantorCount > 0 && outstanding.compareTo(BigDecimal.ZERO) > 0) {
                // Each guarantor is liable for their share of the outstanding balance
                BigDecimal memberLiability = outstanding.divide(
                        BigDecimal.valueOf(guarantorCount), 2, RoundingMode.HALF_UP);
                totalLiability = totalLiability.add(memberLiability);
            }
        }
        
        return totalLiability;
    }

    /**
     * Get details of loans where member is a guarantor (for UI display)
     */
    @Transactional(readOnly = true)
    public List<GuarantorLiabilityDto> getGuarantorLiabilities(Long memberId, Long tenantId) {
        // Use query with eager fetch to avoid lazy loading issues
        List<Loan> activeLoans = loanRepository.findActiveLoansWithGuarantors(tenantId).stream()
                .filter(l -> l.getGuarantors() != null && 
                        l.getGuarantors().stream().anyMatch(g -> g.getId().equals(memberId)))
                .collect(Collectors.toList());

        List<GuarantorLiabilityDto> liabilities = new ArrayList<>();
        for (Loan loan : activeLoans) {
            BigDecimal outstanding = loan.getBalanceOutstanding() != null 
                    ? loan.getBalanceOutstanding() : BigDecimal.ZERO;
            int guarantorCount = loan.getGuarantors() != null ? loan.getGuarantors().size() : 1;
            BigDecimal memberLiability = guarantorCount > 0 
                    ? outstanding.divide(BigDecimal.valueOf(guarantorCount), 2, RoundingMode.HALF_UP)
                    : outstanding;

            liabilities.add(GuarantorLiabilityDto.builder()
                    .loanId(loan.getId())
                    .borrowerName(loan.getMember().getName())
                    .loanAmount(loan.getTotalAmount())
                    .balanceOutstanding(outstanding)
                    .memberLiability(memberLiability)
                    .guarantorCount(guarantorCount)
                    .loanStatus(loan.getStatus().name())
                    .build());
        }
        return liabilities;
    }

    // ==================== PAYOUT PREVIEW ====================

    /**
     * Preview a payout distribution without creating it.
     * Shows how much each member would receive based on current share percentages.
     */
    @Transactional(readOnly = true)
    public PayoutPreviewDto previewPayout(Long tenantId, Payout.PayoutType payoutType, BigDecimal amount, 
                                          Long memberId, Boolean useOwnershipDistribution, BigDecimal distributionPercentage) {
        ShareCalculationDto shareCalc = shareCalculationService.calculateShares(tenantId);
        LiquidityDto liquidity = calculateLiquidity(tenantId);

        if (!shareCalc.getIsConfigured()) {
            throw new RuntimeException("Share calculation is not configured");
        }

        List<MemberShareDto> memberShares = shareCalc.getMemberShares();
        List<PayoutMemberPreviewDto> memberPreviews = new ArrayList<>();

        // Default values
        boolean ownershipBased = useOwnershipDistribution != null && useOwnershipDistribution;
        BigDecimal percentage = distributionPercentage != null ? distributionPercentage : BigDecimal.valueOf(100);
        
        // Validate percentage is between 1 and 100
        if (percentage.compareTo(BigDecimal.ONE) < 0) percentage = BigDecimal.ONE;
        if (percentage.compareTo(BigDecimal.valueOf(100)) > 0) percentage = BigDecimal.valueOf(100);

        BigDecimal payoutAmount = BigDecimal.ZERO;

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
        } else if (ownershipBased) {
            // Ownership-based distribution: each member gets X% of their estimated value
            for (MemberShareDto memberShare : memberShares) {
                if (memberShare.getSharePercentage().compareTo(BigDecimal.ZERO) == 0) {
                    continue; // Skip members with 0% share
                }

                // Calculate member's payout as percentage of their value
                BigDecimal memberValue = memberShare.getMemberValue() != null ? memberShare.getMemberValue() : BigDecimal.ZERO;
                BigDecimal memberPayout = memberValue
                        .multiply(percentage)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                memberPreviews.add(PayoutMemberPreviewDto.builder()
                        .memberId(memberShare.getMemberId())
                        .memberName(memberShare.getMemberName())
                        .sharePercentage(memberShare.getSharePercentage())
                        .contributionAmount(memberShare.getContributionAmount())
                        .payoutAmount(memberPayout)
                        .build());
                
                payoutAmount = payoutAmount.add(memberPayout);
            }
            
            // Cap at liquid cash if needed
            if (payoutAmount.compareTo(liquidity.getLiquidCash()) > 0) {
                // Scale down proportionally
                BigDecimal scaleFactor = liquidity.getLiquidCash().divide(payoutAmount, 6, RoundingMode.HALF_UP);
                payoutAmount = BigDecimal.ZERO;
                for (PayoutMemberPreviewDto preview : memberPreviews) {
                    BigDecimal scaledPayout = preview.getPayoutAmount().multiply(scaleFactor).setScale(2, RoundingMode.HALF_UP);
                    preview.setPayoutAmount(scaledPayout);
                    payoutAmount = payoutAmount.add(scaledPayout);
                }
            }
        } else {
            // Fixed amount distribution: distribute total amount based on share percentages
            payoutAmount = amount != null ? amount : liquidity.getLiquidCash();
            
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
            warning = "Payout amount exceeds available liquid cash. Amount will be capped at available cash.";
        }

        return PayoutPreviewDto.builder()
                .payoutType(payoutType.name())
                .totalAmount(payoutAmount)
                .memberCount(memberPreviews.size())
                .memberPreviews(memberPreviews)
                .liquidity(liquidity)
                .canProceed(canProceed || ownershipBased) // Ownership-based can always proceed (auto-caps)
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

        // ========== VALIDATION: Check payout amount against available funds ==========
        BigDecimal requestedAmount = request.getAmount();
        
        // For all payout types, validate against liquid cash
        if (requestedAmount != null && requestedAmount.compareTo(liquidity.getLiquidCash()) > 0) {
            throw new RuntimeException(String.format(
                    "Payout amount (KES %,.2f) exceeds available liquid cash (KES %,.2f). " +
                    "Note: Outstanding loans and locked investments cannot be distributed.",
                    requestedAmount, liquidity.getLiquidCash()));
        }
        
        // For full distribution, use liquid cash as the amount
        if (request.getPayoutType() == Payout.PayoutType.FULL_DISTRIBUTION) {
            if (liquidity.getLiquidCash().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("No liquid cash available for distribution");
            }
            requestedAmount = liquidity.getLiquidCash();
        }

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
                .payoutAmount(requestedAmount)
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

            // Validate withdrawal amount doesn't exceed member's share value
            if (memberPayout.compareTo(memberShare.getMemberValue()) > 0) {
                throw new RuntimeException(String.format(
                        "Withdrawal amount (KES %,.2f) exceeds member's share value (KES %,.2f)", 
                        memberPayout, memberShare.getMemberValue()));
            }

            // Also validate against liquid cash available
            if (memberPayout.compareTo(liquidity.getLiquidCash()) > 0) {
                throw new RuntimeException(String.format(
                        "Withdrawal amount (KES %,.2f) exceeds available liquid cash (KES %,.2f)", 
                        memberPayout, liquidity.getLiquidCash()));
            }

            // ========== GUARANTOR LIABILITY CHECK ==========
            // Check if member is a guarantor for any active loans
            // They must retain enough value to cover their guarantor obligations
            BigDecimal guarantorLiability = calculateGuarantorLiability(member.getId(), tenantId);
            if (guarantorLiability.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remainingAfterWithdrawal = memberShare.getMemberValue().subtract(memberPayout);
                if (remainingAfterWithdrawal.compareTo(guarantorLiability) < 0) {
                    // Get details for error message
                    List<GuarantorLiabilityDto> liabilities = getGuarantorLiabilities(member.getId(), tenantId);
                    StringBuilder loanDetails = new StringBuilder();
                    for (GuarantorLiabilityDto liability : liabilities) {
                        loanDetails.append(String.format("\n- Loan to %s: Outstanding KES %,.2f (your liability: KES %,.2f)",
                                liability.getBorrowerName(), 
                                liability.getBalanceOutstanding(), 
                                liability.getMemberLiability()));
                    }
                    
                    BigDecimal maxWithdrawable = memberShare.getMemberValue().subtract(guarantorLiability);
                    if (maxWithdrawable.compareTo(BigDecimal.ZERO) < 0) {
                        maxWithdrawable = BigDecimal.ZERO;
                    }
                    
                    throw new RuntimeException(String.format(
                            "Member is a guarantor for active loans with total liability of KES %,.2f. " +
                            "You must retain enough value to cover your guarantor obligations. " +
                            "Maximum withdrawable amount is KES %,.2f.%s",
                            guarantorLiability, maxWithdrawable, loanDetails.toString()));
                }
            }

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
            // Check if using ownership-based distribution
            boolean ownershipBased = request.getUseOwnershipDistribution() != null && request.getUseOwnershipDistribution();
            BigDecimal percentage = request.getDistributionPercentage() != null 
                    ? request.getDistributionPercentage() : BigDecimal.valueOf(100);
            
            // Validate percentage
            if (percentage.compareTo(BigDecimal.ONE) < 0) percentage = BigDecimal.ONE;
            if (percentage.compareTo(BigDecimal.valueOf(100)) > 0) percentage = BigDecimal.valueOf(100);
            
            BigDecimal totalPayoutAmount = BigDecimal.ZERO;
            List<PayoutMember> memberPayouts = new ArrayList<>();
            
            for (MemberShareDto memberShare : memberShares) {
                if (memberShare.getSharePercentage().compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                Member member = memberRepository.findById(memberShare.getMemberId())
                        .orElseThrow(() -> new RuntimeException("Member not found: " + memberShare.getMemberId()));

                BigDecimal memberPayout;
                if (ownershipBased) {
                    // Ownership-based: each member gets X% of their estimated value
                    BigDecimal memberValue = memberShare.getMemberValue() != null ? memberShare.getMemberValue() : BigDecimal.ZERO;
                    memberPayout = memberValue
                            .multiply(percentage)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } else {
                    // Fixed amount: distribute total amount based on share percentages
                    memberPayout = requestedAmount
                            .multiply(memberShare.getSharePercentage())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                }
                
                totalPayoutAmount = totalPayoutAmount.add(memberPayout);

                PayoutMember pm = PayoutMember.builder()
                        .payout(payout)
                        .member(member)
                        .sharePercentage(memberShare.getSharePercentage())
                        .contributionAmount(memberShare.getContributionAmount())
                        .payoutAmount(memberPayout)
                        .status(PayoutMember.PayoutMemberStatus.PENDING)
                        .build();
                memberPayouts.add(pm);
            }
            
            // If ownership-based and total exceeds liquid cash, scale down proportionally
            if (ownershipBased && totalPayoutAmount.compareTo(liquidity.getLiquidCash()) > 0) {
                BigDecimal scaleFactor = liquidity.getLiquidCash().divide(totalPayoutAmount, 6, RoundingMode.HALF_UP);
                totalPayoutAmount = BigDecimal.ZERO;
                for (PayoutMember pm : memberPayouts) {
                    BigDecimal scaledPayout = pm.getPayoutAmount().multiply(scaleFactor).setScale(2, RoundingMode.HALF_UP);
                    pm.setPayoutAmount(scaledPayout);
                    totalPayoutAmount = totalPayoutAmount.add(scaledPayout);
                }
            }
            
            // Update payout amount
            payout.setPayoutAmount(totalPayoutAmount);
            
            // Add all member payouts to the payout
            for (PayoutMember pm : memberPayouts) {
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
     * Get payouts with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<PayoutDto> getPayoutsPaginated(Long tenantId, String status, int page, int size) {
        if (size <= 0) size = 10;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        
        org.springframework.data.domain.Page<Payout> payoutPage;
        
        if (status != null && !status.equalsIgnoreCase("ALL") && !status.trim().isEmpty()) {
            Payout.PayoutStatus payoutStatus = Payout.PayoutStatus.valueOf(status.toUpperCase());
            payoutPage = payoutRepository.findByTenantIdAndStatus(tenantId, payoutStatus, pageable);
        } else {
            payoutPage = payoutRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }
        
        org.springframework.data.domain.Page<PayoutDto> dtoPage = payoutPage.map(this::toDto);
        return PageResponse.from(dtoPage);
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
