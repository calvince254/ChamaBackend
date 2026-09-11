package com.calpex_technologies.chama.scheduler;

import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled task to process overdue loans and apply penalties automatically.
 * Runs daily to check for loans past their due date and apply configured penalties.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoanScheduler {

    private final LoanRepository loanRepository;
    private final LoanPenaltyRepository loanPenaltyRepository;
    private final TenantRepository tenantRepository;

    /**
     * Runs every day at 1:00 AM to process overdue loans.
     * Checks all active loans for overdue payments and applies penalties as configured.
     */
    @Scheduled(cron = "0 0 1 * * *") // Every day at 1:00 AM
    @Transactional
    public void processOverdueLoans() {
        log.info("Starting daily overdue loan processing...");
        
        List<Tenant> tenants = tenantRepository.findAll();
        int totalProcessed = 0;
        int totalPenaltiesApplied = 0;

        for (Tenant tenant : tenants) {
            try {
                int[] result = processOverdueLoansForTenant(tenant.getId());
                totalProcessed += result[0];
                totalPenaltiesApplied += result[1];
            } catch (Exception e) {
                log.error("Error processing overdue loans for tenant {}: {}", tenant.getId(), e.getMessage(), e);
            }
        }

        log.info("Completed overdue loan processing. Loans processed: {}, Penalties applied: {}", 
                totalProcessed, totalPenaltiesApplied);
    }

    /**
     * Process overdue loans for a specific tenant.
     * Can be called manually from admin interface.
     */
    @Transactional
    public int[] processOverdueLoansForTenant(Long tenantId) {
        LocalDate today = LocalDate.now();
        int loansProcessed = 0;
        int penaltiesApplied = 0;

        // Get all active loans that are past due date
        List<Loan> overdueLoans = loanRepository.findOverdueLoans(tenantId, today);

        for (Loan loan : overdueLoans) {
            try {
                boolean penaltyApplied = processOverdueLoan(loan, today);
                loansProcessed++;
                if (penaltyApplied) {
                    penaltiesApplied++;
                }
            } catch (Exception e) {
                log.error("Error processing loan {}: {}", loan.getId(), e.getMessage(), e);
            }
        }

        return new int[]{loansProcessed, penaltiesApplied};
    }

    /**
     * Process a single overdue loan.
     */
    private boolean processOverdueLoan(Loan loan, LocalDate today) {
        // Update loan status to OVERDUE if not already
        if (loan.getStatus() == Loan.LoanStatus.ACTIVE) {
            loan.setStatus(Loan.LoanStatus.OVERDUE);
            loanRepository.save(loan);
            log.info("Loan {} marked as OVERDUE", loan.getId());
        }

        // Check if loan product has penalties enabled
        LoanProduct product = loan.getLoanProduct();
        if (product == null || !product.isPenaltyEnabled()) {
            return false;
        }

        // Check grace period
        LocalDate dueDate = loan.getNextDueDate();
        if (dueDate == null) {
            return false;
        }

        int gracePeriodDays = product.getGracePeriodDays() != null ? product.getGracePeriodDays() : 0;
        LocalDate penaltyStartDate = dueDate.plusDays(gracePeriodDays);

        if (today.isBefore(penaltyStartDate)) {
            // Still within grace period
            return false;
        }

        long daysOverdue = ChronoUnit.DAYS.between(dueDate, today);

        // Check penalty frequency to determine if new penalty should be applied
        return applyPenaltyIfNeeded(loan, product, dueDate, (int) daysOverdue, today);
    }

    /**
     * Apply penalty based on loan product configuration.
     */
    private boolean applyPenaltyIfNeeded(Loan loan, LoanProduct product, LocalDate dueDate, 
                                          int daysOverdue, LocalDate today) {
        
        // Calculate which period we're in
        int periodNumber = calculatePeriodNumber(loan, dueDate, today);

        // Check if penalty for this period already exists
        if (product.getPenaltyFrequency() == LoanProduct.PenaltyFrequency.FLAT) {
            // For FLAT, only one penalty per due date
            if (loanPenaltyRepository.existsByLoanIdAndDueDate(loan.getId(), dueDate)) {
                return false;
            }
        } else {
            // For PER_PERIOD, check if penalty for this period exists
            if (loanPenaltyRepository.existsByLoanIdAndPeriodNumber(loan.getId(), periodNumber)) {
                return false;
            }
        }

        // Calculate penalty amount
        BigDecimal penaltyAmount = calculatePenaltyAmount(loan, product);

        if (penaltyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // Create penalty record
        LoanPenalty penalty = LoanPenalty.builder()
                .tenant(loan.getTenant())
                .loan(loan)
                .member(loan.getMember())
                .amount(penaltyAmount)
                .paidAmount(BigDecimal.ZERO)
                .status(LoanPenalty.PenaltyStatus.UNPAID)
                .reason(generatePenaltyReason(loan, dueDate, daysOverdue, periodNumber))
                .dueDate(dueDate)
                .periodNumber(periodNumber)
                .daysOverdue(daysOverdue)
                .autoGenerated(true)
                .imposedAt(LocalDateTime.now())
                .build();

        loanPenaltyRepository.save(penalty);

        // Update loan's total penalties
        BigDecimal totalPenalties = loan.getTotalPenalties() != null ? loan.getTotalPenalties() : BigDecimal.ZERO;
        loan.setTotalPenalties(totalPenalties.add(penaltyAmount));
        
        // Also add to balance outstanding
        loan.setBalanceOutstanding(loan.getBalanceOutstanding().add(penaltyAmount));
        loanRepository.save(loan);

        log.info("Applied penalty of {} to loan {} for member {}. Period: {}, Days overdue: {}", 
                penaltyAmount, loan.getId(), loan.getMember().getName(), periodNumber, daysOverdue);

        return true;
    }

    /**
     * Calculate penalty amount based on product configuration.
     */
    private BigDecimal calculatePenaltyAmount(Loan loan, LoanProduct product) {
        BigDecimal penaltyRate = product.getPenaltyRate();
        if (penaltyRate == null || penaltyRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseAmount;
        switch (product.getPenaltyType()) {
            case PERCENTAGE_OF_PRINCIPAL:
                baseAmount = loan.getPrincipal();
                return baseAmount.multiply(penaltyRate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                
            case PERCENTAGE_OF_OUTSTANDING:
                baseAmount = loan.getBalanceOutstanding();
                return baseAmount.multiply(penaltyRate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                
            case FIXED_AMOUNT:
                return penaltyRate; // penaltyRate is the fixed amount in this case
                
            default:
                return BigDecimal.ZERO;
        }
    }

    /**
     * Calculate which period number this is (for PER_PERIOD penalties).
     * Period 1 = first month overdue, Period 2 = second month overdue, etc.
     */
    private int calculatePeriodNumber(Loan loan, LocalDate dueDate, LocalDate today) {
        LoanProduct product = loan.getLoanProduct();
        if (product == null) {
            return 1;
        }

        int graceDays = product.getGracePeriodDays() != null ? product.getGracePeriodDays() : 0;
        LocalDate penaltyStart = dueDate.plusDays(graceDays);
        
        if (today.isBefore(penaltyStart)) {
            return 0;
        }

        long daysOverdue = ChronoUnit.DAYS.between(penaltyStart, today);
        
        // Calculate period based on repayment period type
        if (product.getRepaymentPeriodType() == LoanProduct.PeriodType.DAYS) {
            int periodLength = product.getRepaymentPeriod() != null ? product.getRepaymentPeriod() : 30;
            return (int) (daysOverdue / periodLength) + 1;
        } else {
            // MONTHS - approximately 30 days per period
            return (int) (daysOverdue / 30) + 1;
        }
    }

    /**
     * Generate human-readable reason for the penalty.
     */
    private String generatePenaltyReason(Loan loan, LocalDate dueDate, int daysOverdue, int periodNumber) {
        String productName = loan.getLoanProduct() != null ? loan.getLoanProduct().getName() : "Loan";
        return String.format("Late payment penalty for %s. Due date: %s. Days overdue: %d. Period: %d",
                productName, dueDate, daysOverdue, periodNumber);
    }

    /**
     * Mark loans as DEFAULTED if they've been overdue for too long.
     * Runs weekly on Sundays at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * SUN") // Every Sunday at 2:00 AM
    @Transactional
    public void markDefaultedLoans() {
        log.info("Starting weekly defaulted loan check...");
        
        LocalDate today = LocalDate.now();
        int defaultedCount = 0;

        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            List<Loan> overdueLoans = loanRepository.findOverdueByTenantId(tenant.getId());
            
            for (Loan loan : overdueLoans) {
                if (loan.getNextDueDate() == null) continue;
                
                // Default threshold: 90 days overdue
                long daysOverdue = ChronoUnit.DAYS.between(loan.getNextDueDate(), today);
                
                if (daysOverdue >= 90 && loan.getStatus() != Loan.LoanStatus.DEFAULTED) {
                    loan.setStatus(Loan.LoanStatus.DEFAULTED);
                    loanRepository.save(loan);
                    defaultedCount++;
                    log.warn("Loan {} marked as DEFAULTED. {} days overdue", loan.getId(), daysOverdue);
                }
            }
        }
        
        log.info("Completed defaulted loan check. {} loans marked as defaulted", defaultedCount);
    }
}
