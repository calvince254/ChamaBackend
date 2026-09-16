package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.MemberShareDto;
import com.calpex_technologies.chama.dto.ShareCalculationDto;
import com.calpex_technologies.chama.dto.ShareSettingsDto;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShareCalculationService {

    private final ShareSettingsRepository shareSettingsRepository;
    private final TenantRepository tenantRepository;
    private final MemberRepository memberRepository;
    private final ContributionRepository contributionRepository;
    private final FineRepository fineRepository;
    private final PenaltyRepository penaltyRepository;
    private final LoanRepository loanRepository;
    private final InvestmentRepository investmentRepository;
    private final PayoutMemberRepository payoutMemberRepository;
    private final TransactionRepository transactionRepository;

    // ==================== SETTINGS MANAGEMENT ====================

    public ShareSettingsDto getShareSettings(Long tenantId) {
        ShareSettings settings = shareSettingsRepository.findByTenantId(tenantId).orElse(null);
        if (settings == null) {
            return ShareSettingsDto.builder()
                    .shareContributionTypeIds(new HashSet<>())
                    .poolContributionTypeIds(new HashSet<>())
                    .includeFines(false)
                    .includeLoanInterest(false)
                    .includeInvestmentReturns(false)
                    .includePenalties(false)
                    .includeProcessingFees(false)
                    .build();
        }
        return toDto(settings);
    }

    @Transactional
    public ShareSettingsDto saveShareSettings(ShareSettingsDto dto, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        ShareSettings settings = shareSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> ShareSettings.builder().tenant(tenant).build());

        // Update settings
        settings.setShareContributionTypeIds(dto.getShareContributionTypeIds() != null 
                ? dto.getShareContributionTypeIds() : new HashSet<>());
        settings.setPoolContributionTypeIds(dto.getPoolContributionTypeIds() != null 
                ? dto.getPoolContributionTypeIds() : new HashSet<>());
        settings.setIncludeFines(dto.getIncludeFines() != null ? dto.getIncludeFines() : false);
        settings.setIncludeLoanInterest(dto.getIncludeLoanInterest() != null ? dto.getIncludeLoanInterest() : false);
        settings.setIncludeInvestmentReturns(dto.getIncludeInvestmentReturns() != null ? dto.getIncludeInvestmentReturns() : false);
        settings.setIncludePenalties(dto.getIncludePenalties() != null ? dto.getIncludePenalties() : false);
        settings.setIncludeProcessingFees(dto.getIncludeProcessingFees() != null ? dto.getIncludeProcessingFees() : false);

        settings = shareSettingsRepository.save(settings);
        return toDto(settings);
    }

    // ==================== SHARE CALCULATION ====================

    public ShareCalculationDto calculateShares(Long tenantId) {
        ShareSettings settings = shareSettingsRepository.findByTenantId(tenantId).orElse(null);
        
        if (settings == null || settings.getShareContributionTypeIds().isEmpty()) {
            return ShareCalculationDto.builder()
                    .isConfigured(false)
                    .totalShareContributions(BigDecimal.ZERO)
                    .totalPoolValue(BigDecimal.ZERO)
                    .memberShares(new ArrayList<>())
                    .calculatedAt(LocalDateTime.now().toString())
                    .build();
        }

        List<Member> members = memberRepository.findByTenantIdAndStatus(tenantId, Member.MemberStatus.ACTIVE);
        List<Contribution> allContributions = contributionRepository.findByTenantIdOrderByMonthDesc(tenantId);

        // ==================== STEP 1: Calculate Share Percentages ====================
        // Filter contributions by selected share contribution types (PAID only)
        Set<Long> shareTypeIds = settings.getShareContributionTypeIds();
        List<Contribution> shareContributions = allContributions.stream()
                .filter(c -> c.getContributionType() != null 
                        && shareTypeIds.contains(c.getContributionType().getId())
                        && c.getStatus() == Contribution.ContributionStatus.PAID)
                .collect(Collectors.toList());

        // Sum contributions per member
        Map<Long, BigDecimal> memberContributions = new HashMap<>();
        BigDecimal totalShareContributions = BigDecimal.ZERO;

        for (Contribution c : shareContributions) {
            Long memberId = c.getMember().getId();
            BigDecimal paid = c.getAmountPaid() != null ? c.getAmountPaid() : BigDecimal.ZERO;
            memberContributions.merge(memberId, paid, BigDecimal::add);
            totalShareContributions = totalShareContributions.add(paid);
        }

        // ==================== STEP 2: Calculate Profit Pool ====================
        BigDecimal poolContributions = BigDecimal.ZERO;
        BigDecimal poolFines = BigDecimal.ZERO;
        BigDecimal poolLoanInterest = BigDecimal.ZERO;
        BigDecimal poolInvestmentReturns = BigDecimal.ZERO;
        BigDecimal poolPenalties = BigDecimal.ZERO;
        BigDecimal poolProcessingFees = BigDecimal.ZERO;

        // Pool contributions (may be different from share calculation types)
        Set<Long> poolTypeIds = settings.getPoolContributionTypeIds();
        if (!poolTypeIds.isEmpty()) {
            poolContributions = allContributions.stream()
                    .filter(c -> c.getContributionType() != null 
                            && poolTypeIds.contains(c.getContributionType().getId())
                            && c.getStatus() == Contribution.ContributionStatus.PAID)
                    .map(c -> c.getAmountPaid() != null ? c.getAmountPaid() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Fines collected (PAID status)
        if (Boolean.TRUE.equals(settings.getIncludeFines())) {
            poolFines = calculateTotalFinesCollected(tenantId);
        }

        // Loan interest collected (from repayments on cleared/active loans)
        if (Boolean.TRUE.equals(settings.getIncludeLoanInterest())) {
            poolLoanInterest = calculateTotalLoanInterestCollected(tenantId);
        }

        // Investment returns (gains from sold or current investments)
        if (Boolean.TRUE.equals(settings.getIncludeInvestmentReturns())) {
            poolInvestmentReturns = calculateTotalInvestmentReturns(tenantId);
        }

        // Penalties collected (PAID status)
        if (Boolean.TRUE.equals(settings.getIncludePenalties())) {
            poolPenalties = penaltyRepository.getTotalPaidPenaltiesForTenant(tenantId);
            if (poolPenalties == null) poolPenalties = BigDecimal.ZERO;
        }

        // Processing fees collected (from PROCESSING_FEE transactions)
        if (Boolean.TRUE.equals(settings.getIncludeProcessingFees())) {
            poolProcessingFees = calculateTotalProcessingFeesCollected(tenantId);
        }

        BigDecimal totalPoolValue = poolContributions
                .add(poolFines)
                .add(poolLoanInterest)
                .add(poolInvestmentReturns)
                .add(poolPenalties)
                .add(poolProcessingFees);

        // ==================== STEP 2.5: Subtract Completed Payouts ====================
        // Get total paid out to members (reduces the pool)
        BigDecimal totalPaidOut = payoutMemberRepository.sumPaidAmountsByTenantId(tenantId);
        if (totalPaidOut == null) totalPaidOut = BigDecimal.ZERO;
        
        // Also get paid amounts per member for individual share calculation
        Map<Long, BigDecimal> memberPayouts = new HashMap<>();
        List<Object[]> memberPayoutSums = payoutMemberRepository.sumPaidAmountsByMemberAndTenantId(tenantId);
        for (Object[] row : memberPayoutSums) {
            Long memberId = (Long) row[0];
            BigDecimal paidAmount = (BigDecimal) row[1];
            memberPayouts.put(memberId, paidAmount);
        }

        // Reduce total pool by amount already paid out
        // Allow negative values to show when more has been paid out than available
        totalPoolValue = totalPoolValue.subtract(totalPaidOut);

        // ==================== STEP 3: Calculate Member Shares ====================
        // First, adjust contributions by subtracting individual payouts
        // When a member withdraws, their contribution base is reduced proportionally
        Map<Long, BigDecimal> adjustedContributions = new HashMap<>();
        BigDecimal adjustedTotalContributions = BigDecimal.ZERO;

        for (Member member : members) {
            BigDecimal memberContribution = memberContributions.getOrDefault(member.getId(), BigDecimal.ZERO);
            BigDecimal memberPayout = memberPayouts.getOrDefault(member.getId(), BigDecimal.ZERO);
            
            // Reduce contribution by the proportion of payout to original member value
            // If member got paid their full share, their contribution basis goes to zero
            BigDecimal adjustedContribution = memberContribution;
            if (memberPayout.compareTo(BigDecimal.ZERO) > 0 && memberContribution.compareTo(BigDecimal.ZERO) > 0) {
                // Calculate what percentage of their share they withdrew
                // For simplicity: reduce contribution proportionally to payout received vs original contribution
                // This ensures their ownership % decreases after withdrawal
                BigDecimal reductionRatio = memberPayout.divide(
                        memberContribution.add(memberPayout), 6, RoundingMode.HALF_UP);
                BigDecimal reduction = memberContribution.multiply(reductionRatio);
                adjustedContribution = memberContribution.subtract(reduction);
                if (adjustedContribution.compareTo(BigDecimal.ZERO) < 0) {
                    adjustedContribution = BigDecimal.ZERO;
                }
            }
            
            adjustedContributions.put(member.getId(), adjustedContribution);
            adjustedTotalContributions = adjustedTotalContributions.add(adjustedContribution);
        }

        List<MemberShareDto> memberShares = new ArrayList<>();

        for (Member member : members) {
            BigDecimal originalContribution = memberContributions.getOrDefault(member.getId(), BigDecimal.ZERO);
            BigDecimal adjustedContribution = adjustedContributions.getOrDefault(member.getId(), BigDecimal.ZERO);
            BigDecimal memberPayout = memberPayouts.getOrDefault(member.getId(), BigDecimal.ZERO);
            
            // Calculate share percentage based on adjusted contributions
            BigDecimal sharePercentage = BigDecimal.ZERO;
            if (adjustedTotalContributions.compareTo(BigDecimal.ZERO) > 0) {
                sharePercentage = adjustedContribution
                        .multiply(BigDecimal.valueOf(100))
                        .divide(adjustedTotalContributions, 4, RoundingMode.HALF_UP);
            }

            // Calculate member value based on percentage of remaining pool
            // Can be negative if pool is negative (more paid out than available)
            BigDecimal memberValue = BigDecimal.ZERO;
            if (sharePercentage.compareTo(BigDecimal.ZERO) > 0) {
                memberValue = totalPoolValue
                        .multiply(sharePercentage)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            memberShares.add(MemberShareDto.builder()
                    .memberId(member.getId())
                    .memberName(member.getName())
                    .memberNo(member.getMemberNo())
                    .contributionAmount(adjustedContribution) // Show adjusted contribution
                    .sharePercentage(sharePercentage.setScale(2, RoundingMode.HALF_UP))
                    .memberValue(memberValue)
                    .status(member.getStatus().name())
                    .totalPaidOut(memberPayout) // Track how much they've been paid
                    .build());
        }

        // Sort by share percentage descending
        memberShares.sort((a, b) -> b.getSharePercentage().compareTo(a.getSharePercentage()));

        // Update last calculated timestamp
        updateLastCalculatedAt(settings);

        return ShareCalculationDto.builder()
                .isConfigured(true)
                .totalShareContributions(adjustedTotalContributions)
                .poolContributions(poolContributions)
                .poolFines(poolFines)
                .poolLoanInterest(poolLoanInterest)
                .poolInvestmentReturns(poolInvestmentReturns)
                .poolPenalties(poolPenalties)
                .poolProcessingFees(poolProcessingFees)
                .totalPaidOut(totalPaidOut)
                .totalPoolValue(totalPoolValue)
                .memberShares(memberShares)
                .calculatedAt(LocalDateTime.now().toString())
                .build();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Calculate total fines collected (paid fines)
     */
    private BigDecimal calculateTotalFinesCollected(Long tenantId) {
        // Get all fines for the tenant's meetings
        // We need to query through meetings that belong to this tenant
        // For now, we'll use direct query approach
        List<Fine> allFines = fineRepository.findAll().stream()
                .filter(f -> f.getMeeting() != null 
                        && f.getMeeting().getTenant() != null 
                        && f.getMeeting().getTenant().getId().equals(tenantId)
                        && f.getStatus() == Fine.FineStatus.PAID)
                .collect(Collectors.toList());
        
        return allFines.stream()
                .map(f -> f.getPaidAmount() != null ? f.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total loan interest collected from LOAN_INTEREST transactions
     * This uses the actual recorded interest transactions for accuracy
     */
    private BigDecimal calculateTotalLoanInterestCollected(Long tenantId) {
        // Use LOAN_INTEREST transactions (recorded when repayments are made)
        List<Transaction> interestTransactions = transactionRepository
                .findByTenantIdAndType(tenantId, Transaction.TransactionType.LOAN_INTEREST);
        
        BigDecimal totalInterestCollected = interestTransactions.stream()
                .filter(t -> t.getDirection() == Transaction.TransactionDirection.IN)
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // If no LOAN_INTEREST transactions yet (legacy data), fall back to calculation
        if (totalInterestCollected.compareTo(BigDecimal.ZERO) == 0) {
            totalInterestCollected = calculateLoanInterestFromLoans(tenantId);
        }
        
        return totalInterestCollected;
    }
    
    /**
     * Fallback method: Calculate loan interest from loan entities (for legacy data)
     */
    private BigDecimal calculateLoanInterestFromLoans(Long tenantId) {
        List<Loan> loans = loanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.CLEARED 
                        || l.getStatus() == Loan.LoanStatus.ACTIVE
                        || l.getStatus() == Loan.LoanStatus.OVERDUE)
                .collect(Collectors.toList());

        BigDecimal totalInterestCollected = BigDecimal.ZERO;

        for (Loan loan : loans) {
            BigDecimal totalPaid = loan.getTotalPaid() != null ? loan.getTotalPaid() : BigDecimal.ZERO;
            BigDecimal principal = loan.getPrincipal() != null ? loan.getPrincipal() : BigDecimal.ZERO;
            BigDecimal totalInterest = loan.getTotalInterest() != null ? loan.getTotalInterest() : BigDecimal.ZERO;
            BigDecimal totalAmount = loan.getTotalAmount() != null ? loan.getTotalAmount() : principal.add(totalInterest);

            if (loan.getStatus() == Loan.LoanStatus.CLEARED) {
                // For cleared loans, all interest was collected
                totalInterestCollected = totalInterestCollected.add(totalInterest);
            } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0 && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                // For active/overdue loans, calculate proportional interest
                BigDecimal interestRatio = totalInterest.divide(totalAmount, 6, RoundingMode.HALF_UP);
                BigDecimal interestPaid = totalPaid.multiply(interestRatio).setScale(2, RoundingMode.HALF_UP);
                totalInterestCollected = totalInterestCollected.add(interestPaid);
            }
        }

        return totalInterestCollected;
    }

    /**
     * Calculate total investment returns (gains)
     * For SOLD investments: soldAmount - amountInvested
     * For ACTIVE investments with gains: currentValue - amountInvested (unrealized)
     */
    private BigDecimal calculateTotalInvestmentReturns(Long tenantId) {
        List<Investment> investments = investmentRepository.findApprovedByTenantId(tenantId);
        
        BigDecimal totalReturns = BigDecimal.ZERO;

        for (Investment inv : investments) {
            BigDecimal amountInvested = inv.getAmountInvested() != null ? inv.getAmountInvested() : BigDecimal.ZERO;
            
            if (inv.getStatus() == Investment.InvestmentStatus.SOLD) {
                // Realized gain from sold investments
                BigDecimal soldAmount = inv.getSoldAmount() != null ? inv.getSoldAmount() : BigDecimal.ZERO;
                BigDecimal gain = soldAmount.subtract(amountInvested);
                if (gain.compareTo(BigDecimal.ZERO) > 0) {
                    totalReturns = totalReturns.add(gain);
                }
            } else if (inv.getStatus() == Investment.InvestmentStatus.ACTIVE) {
                // Unrealized gain from active investments (optional - can be configured)
                BigDecimal currentValue = inv.getCurrentValue() != null ? inv.getCurrentValue() : amountInvested;
                BigDecimal unrealizedGain = currentValue.subtract(amountInvested);
                if (unrealizedGain.compareTo(BigDecimal.ZERO) > 0) {
                    totalReturns = totalReturns.add(unrealizedGain);
                }
            }
            // Note: WRITTEN_OFF investments represent losses, not included in returns
        }

        return totalReturns;
    }

    /**
     * Calculate total processing fees collected from loan disbursements and repayments
     * Uses PROCESSING_FEE transaction type
     */
    private BigDecimal calculateTotalProcessingFeesCollected(Long tenantId) {
        List<Transaction> processingFeeTransactions = transactionRepository
                .findByTenantIdAndType(tenantId, Transaction.TransactionType.PROCESSING_FEE);
        
        return processingFeeTransactions.stream()
                .filter(t -> t.getDirection() == Transaction.TransactionDirection.IN)
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    private void updateLastCalculatedAt(ShareSettings settings) {
        settings.setLastCalculatedAt(LocalDateTime.now());
        shareSettingsRepository.save(settings);
    }

    private ShareSettingsDto toDto(ShareSettings settings) {
        return ShareSettingsDto.builder()
                .id(settings.getId())
                .shareContributionTypeIds(settings.getShareContributionTypeIds())
                .poolContributionTypeIds(settings.getPoolContributionTypeIds())
                .includeFines(settings.getIncludeFines())
                .includeLoanInterest(settings.getIncludeLoanInterest())
                .includeInvestmentReturns(settings.getIncludeInvestmentReturns())
                .includePenalties(settings.getIncludePenalties())
                .includeProcessingFees(settings.getIncludeProcessingFees())
                .lastCalculatedAt(settings.getLastCalculatedAt() != null 
                        ? settings.getLastCalculatedAt().toString() : null)
                .build();
    }
}
