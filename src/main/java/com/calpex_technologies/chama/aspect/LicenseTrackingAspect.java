package com.calpex_technologies.chama.aspect;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import com.calpex_technologies.chama.service.LicenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Aspect that automatically records license transactions when billable events occur.
 * Tracks contributions, fines, loan disbursements, and penalties.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseTrackingAspect {

    private final LicenseService licenseService;
    private final ContributionRepository contributionRepository;
    private final FineRepository fineRepository;
    private final PenaltyRepository penaltyRepository;
    private final LoanRepository loanRepository;

    /**
     * Track contribution payments when payment is recorded
     */
    @AfterReturning(
            pointcut = "execution(* com.calpex_technologies.chama.service.ContributionService.recordPayment(..))",
            returning = "result")
    public void trackContributionPayment(JoinPoint joinPoint, Object result) {
        try {
            if (result instanceof ContributionDto dto && dto.getId() != null) {
                // Fetch the actual entity to get tenant info
                contributionRepository.findById(dto.getId()).ifPresent(this::recordContribution);
            }
        } catch (Exception e) {
            log.error("Failed to track contribution for licensing: {}", e.getMessage());
        }
    }

    /**
     * Track contribution payments via recordPaymentForMember
     */
    @AfterReturning(
            pointcut = "execution(* com.calpex_technologies.chama.service.ContributionService.recordPaymentForMember(..))",
            returning = "result")
    public void trackContributionPaymentForMember(JoinPoint joinPoint, Object result) {
        try {
            if (result instanceof ContributionDto dto && dto.getId() != null) {
                contributionRepository.findById(dto.getId()).ifPresent(this::recordContribution);
            }
        } catch (Exception e) {
            log.error("Failed to track contribution for licensing: {}", e.getMessage());
        }
    }

    /**
     * Track fine payments when marked as paid
     */
    @AfterReturning(
            pointcut = "execution(* com.calpex_technologies.chama.service.FineService.markFinePaid(..)) || " +
                       "execution(* com.calpex_technologies.chama.service.FineService.recordPayment(..))",
            returning = "result")
    public void trackFinePaid(JoinPoint joinPoint, Object result) {
        try {
            if (result instanceof FineDto dto && dto.getId() != null) {
                fineRepository.findById(dto.getId()).ifPresent(this::recordFine);
            }
        } catch (Exception e) {
            log.error("Failed to track fine payment for licensing: {}", e.getMessage());
        }
    }

    /**
     * Track loan disbursements when loan is disbursed
     */
    @AfterReturning(
            pointcut = "execution(* com.calpex_technologies.chama.service.LoanService.disburseLoan(..))",
            returning = "result")
    public void trackLoanDisbursement(JoinPoint joinPoint, Object result) {
        try {
            if (result instanceof LoanDto dto && dto.getId() != null) {
                loanRepository.findById(dto.getId()).ifPresent(this::recordLoanDisbursement);
            }
        } catch (Exception e) {
            log.error("Failed to track loan disbursement for licensing: {}", e.getMessage());
        }
    }

    /**
     * Track penalty payments
     */
    @AfterReturning(
            pointcut = "execution(* com.calpex_technologies.chama.service.PenaltyService.recordPayment(..))",
            returning = "result")
    public void trackPenaltyPayment(JoinPoint joinPoint, Object result) {
        try {
            if (result instanceof PenaltyDto dto && dto.getId() != null) {
                penaltyRepository.findById(dto.getId()).ifPresent(this::recordPenalty);
            }
        } catch (Exception e) {
            log.error("Failed to track penalty payment for licensing: {}", e.getMessage());
        }
    }

    // ==================== HELPER METHODS ====================

    private void recordContribution(Contribution contribution) {
        // Use amountPaid - this is what was actually paid
        if (contribution.getAmountPaid() == null || contribution.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Long tenantId = contribution.getMember().getTenant().getId();
        String description = String.format("Contribution from %s - %s",
                contribution.getMember().getName(),
                contribution.getContributionType() != null ? contribution.getContributionType().getName() : "N/A");

        licenseService.recordTransaction(
                tenantId,
                LicenseTransaction.TransactionType.CONTRIBUTION,
                contribution.getAmountPaid(),
                contribution.getId(),
                "Contribution",
                description);

        log.debug("Recorded contribution transaction for license: contribution={}, tenant={}", 
                contribution.getId(), tenantId);
    }

    private void recordFine(Fine fine) {
        if (fine.getAmount() == null || fine.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Long tenantId = fine.getMember().getTenant().getId();
        String description = String.format("Fine paid by %s - %s",
                fine.getMember().getName(),
                fine.getFineType() != null ? fine.getFineType().getName() : "N/A");

        licenseService.recordTransaction(
                tenantId,
                LicenseTransaction.TransactionType.FINE,
                fine.getAmount(),
                fine.getId(),
                "Fine",
                description);

        log.debug("Recorded fine transaction for license: fine={}, tenant={}", fine.getId(), tenantId);
    }

    private void recordLoanDisbursement(Loan loan) {
        if (loan.getPrincipal() == null || loan.getPrincipal().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Long tenantId = loan.getMember().getTenant().getId();
        String description = String.format("Loan disbursed to %s - %s",
                loan.getMember().getName(),
                loan.getLoanProduct() != null ? loan.getLoanProduct().getName() : "N/A");

        licenseService.recordTransaction(
                tenantId,
                LicenseTransaction.TransactionType.LOAN_DISBURSEMENT,
                loan.getPrincipal(),
                loan.getId(),
                "Loan",
                description);

        log.debug("Recorded loan disbursement for license: loan={}, tenant={}", loan.getId(), tenantId);
    }

    private void recordPenalty(Penalty penalty) {
        if (penalty.getAmount() == null || penalty.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Long tenantId = penalty.getMember().getTenant().getId();
        String description = String.format("Penalty paid by %s",
                penalty.getMember().getName());

        licenseService.recordTransaction(
                tenantId,
                LicenseTransaction.TransactionType.PENALTY,
                penalty.getAmount(),
                penalty.getId(),
                "Penalty",
                description);

        log.debug("Recorded penalty transaction for license: penalty={}, tenant={}", penalty.getId(), tenantId);
    }
}
