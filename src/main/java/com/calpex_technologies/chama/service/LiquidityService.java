package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.LiquidityDto;
import com.calpex_technologies.chama.dto.ShareCalculationDto;
import com.calpex_technologies.chama.entity.Investment;
import com.calpex_technologies.chama.entity.Loan;
import com.calpex_technologies.chama.repository.InvestmentRepository;
import com.calpex_technologies.chama.repository.LoanRepository;
import com.calpex_technologies.chama.repository.PayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for calculating liquidity and available cash.
 * This is a shared service used by PayoutService, LoanService, and InvestmentService
 * to ensure consistent liquidity calculations across the application.
 * 
 * Liquid Cash = Total Pool Value - Locked Investments - Outstanding Loans
 * 
 * This ensures we don't:
 * - Disburse loans we don't have cash for
 * - Invest money we don't have
 * - Pay out more than available liquid cash
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiquidityService {

    private final ShareCalculationService shareCalculationService;
    private final LoanRepository loanRepository;
    private final InvestmentRepository investmentRepository;
    private final PayoutRepository payoutRepository;

    /**
     * Calculate the full liquidity breakdown for a tenant.
     * Liquid Cash = Total Pool Value - Locked Investments - Outstanding Loans
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
        BigDecimal liquidCash = totalPoolValue
                .subtract(lockedInInvestments)
                .subtract(outstandingLoans);
        
        // Ensure liquid cash is not negative
        if (liquidCash.compareTo(BigDecimal.ZERO) < 0) {
            liquidCash = BigDecimal.ZERO;
        }

        // Get completed payouts total
        BigDecimal totalPaidOut = BigDecimal.ZERO;
        if (payoutRepository != null) {
            totalPaidOut = payoutRepository.sumCompletedPayoutsByTenantId(tenantId);
            if (totalPaidOut == null) totalPaidOut = BigDecimal.ZERO;
        }

        boolean hasActivePayout = payoutRepository != null && payoutRepository.hasActivePayouts(tenantId);

        return LiquidityDto.builder()
                .totalPoolValue(totalPoolValue)
                .liquidCash(liquidCash)
                .lockedInInvestments(lockedInInvestments)
                .outstandingLoans(outstandingLoans)
                .totalPaidOut(totalPaidOut)
                .hasActivePayout(hasActivePayout)
                .calculatedAt(LocalDateTime.now().toString())
                .build();
    }

    /**
     * Get just the liquid cash amount (simplified method for validation checks).
     * Use this when you only need to check if there's enough cash for an operation.
     */
    @Transactional(readOnly = true)
    public BigDecimal getLiquidCash(Long tenantId) {
        return calculateLiquidity(tenantId).getLiquidCash();
    }

    /**
     * Check if there's enough liquid cash for a given amount.
     * Returns true if liquidCash >= amount.
     */
    @Transactional(readOnly = true)
    public boolean hasEnoughLiquidCash(Long tenantId, BigDecimal amount) {
        BigDecimal liquidCash = getLiquidCash(tenantId);
        return liquidCash.compareTo(amount) >= 0;
    }

    /**
     * Validate that there's enough liquid cash for an operation.
     * Throws RuntimeException with detailed message if not enough cash.
     * 
     * @param tenantId The tenant ID
     * @param amount The amount needed
     * @param operationType Description of the operation (e.g., "loan disbursement", "investment")
     */
    @Transactional(readOnly = true)
    public void validateLiquidCash(Long tenantId, BigDecimal amount, String operationType) {
        LiquidityDto liquidity = calculateLiquidity(tenantId);
        
        if (liquidity.getLiquidCash().compareTo(amount) < 0) {
            throw new RuntimeException(String.format(
                    "Insufficient liquid cash for %s. " +
                    "Requested: KES %,.2f, Available: KES %,.2f. " +
                    "Total Pool: KES %,.2f, Locked in Investments: KES %,.2f, Outstanding Loans: KES %,.2f.",
                    operationType,
                    amount,
                    liquidity.getLiquidCash(),
                    liquidity.getTotalPoolValue(),
                    liquidity.getLockedInInvestments(),
                    liquidity.getOutstandingLoans()
            ));
        }
    }

    /**
     * Calculate outstanding loans (principal + interest remaining).
     * Includes ACTIVE, OVERDUE, and APPROVED (awaiting disbursement) loans.
     */
    public BigDecimal calculateOutstandingLoans(Long tenantId) {
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
     * Calculate locked in investments (active investments at current value).
     * Only includes ACTIVE investments with APPROVED approval status.
     */
    public BigDecimal calculateLockedInvestments(Long tenantId) {
        List<Investment> activeInvestments = investmentRepository.findApprovedByTenantId(tenantId).stream()
                .filter(i -> i.getStatus() == Investment.InvestmentStatus.ACTIVE)
                .collect(Collectors.toList());

        BigDecimal locked = BigDecimal.ZERO;
        for (Investment inv : activeInvestments) {
            // Use amountInvested (principal) not currentValue for liquidity calculation
            // This is the actual cash that was invested
            BigDecimal investedAmount = inv.getAmountInvested();
            if (investedAmount != null) {
                locked = locked.add(investedAmount);
            }
        }
        return locked;
    }

    /**
     * Calculate locked investments using invested amount (principal) only.
     * Use this for strict cash-based liquidity calculation.
     */
    public BigDecimal calculateLockedInvestmentsPrincipal(Long tenantId) {
        List<Investment> activeInvestments = investmentRepository.findApprovedByTenantId(tenantId).stream()
                .filter(i -> i.getStatus() == Investment.InvestmentStatus.ACTIVE)
                .collect(Collectors.toList());

        BigDecimal locked = BigDecimal.ZERO;
        for (Investment inv : activeInvestments) {
            BigDecimal principal = inv.getAmountInvested();
            if (principal != null) {
                locked = locked.add(principal);
            }
        }
        return locked;
    }
}
