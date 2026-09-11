package com.calpex_technologies.chama.scheduler;

import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import com.calpex_technologies.chama.service.PenaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Scheduler for contribution-related tasks:
 * - Generate contribution records for active contribution types
 * - Check for late payments and impose penalties
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContributionScheduler {

    private final TenantRepository tenantRepository;
    private final MemberRepository memberRepository;
    private final ContributionTypeRepository contributionTypeRepository;
    private final ContributionRepository contributionRepository;
    private final PenaltyService penaltyService;

    /**
     * Run daily at 1:00 AM to:
     * 1. Generate contribution records for the current period
     * 2. Check for overdue contributions and impose late payment penalties
     */
    @Scheduled(cron = "0 0 1 * * *") // Every day at 1:00 AM
    @Transactional
    public void processContributions() {
        log.info("Starting daily contribution processing");
        
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            try {
                processContributionsForTenant(tenant);
            } catch (Exception e) {
                log.error("Error processing contributions for tenant {}: {}", tenant.getId(), e.getMessage());
            }
        }
        
        log.info("Completed daily contribution processing");
    }

    /**
     * Process contributions for a specific tenant
     */
    @Transactional
    public void processContributionsForTenant(Tenant tenant) {
        Long tenantId = tenant.getId();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        // Get active contribution types for this tenant
        List<ContributionType> activeTypes = contributionTypeRepository.findByTenantIdAndActiveTrue(tenantId);
        
        if (activeTypes.isEmpty()) {
            log.debug("No active contribution types for tenant {}", tenantId);
            return;
        }

        // Get all active members
        List<Member> members = memberRepository.findByTenantIdAndStatus(tenantId, Member.MemberStatus.ACTIVE);
        
        if (members.isEmpty()) {
            log.debug("No active members for tenant {}", tenantId);
            return;
        }

        for (ContributionType contributionType : activeTypes) {
            processContributionType(tenant, contributionType, members, today, currentMonth);
        }
    }

    /**
     * Process a single contribution type - generate records and check for late payments
     */
    private void processContributionType(Tenant tenant, ContributionType contributionType, 
                                         List<Member> members, LocalDate today, YearMonth currentMonth) {
        
        // Determine the period based on frequency
        YearMonth contributionPeriod = determineCurrentPeriod(contributionType, currentMonth);
        LocalDate dueDate = calculateDueDate(contributionType, contributionPeriod);
        
        for (Member member : members) {
            // 1. Create contribution record if it doesn't exist
            createContributionRecordIfNeeded(tenant, contributionType, member, contributionPeriod, dueDate);
            
            // 2. Check for late payments and impose penalties
            checkAndImposeLatePaymentPenalty(tenant, contributionType, member, today, contributionPeriod);
        }
    }

    /**
     * Create a contribution record for a member if it doesn't exist for the period
     */
    private void createContributionRecordIfNeeded(Tenant tenant, ContributionType contributionType, 
                                                   Member member, YearMonth period, LocalDate dueDate) {
        // Check if contribution record already exists
        boolean exists = contributionRepository.existsByContributionTypeIdAndMemberIdAndMonth(
                contributionType.getId(), member.getId(), period);
        
        if (!exists) {
            Contribution contribution = Contribution.builder()
                    .tenant(tenant)
                    .member(member)
                    .contributionType(contributionType)
                    .amountDue(contributionType.getAmount())
                    .amountPaid(BigDecimal.ZERO)
                    .month(period)
                    .dueDate(dueDate)
                    .status(Contribution.ContributionStatus.PENDING)
                    .penaltyApplied(false)
                    .penalty(BigDecimal.ZERO)
                    .build();
            
            contributionRepository.save(contribution);
            log.debug("Created contribution record for member {} type {} period {}", 
                    member.getId(), contributionType.getName(), period);
        }
    }

    /**
     * Check for late payments and impose penalties
     */
    private void checkAndImposeLatePaymentPenalty(Tenant tenant, ContributionType contributionType, 
                                                   Member member, LocalDate today, YearMonth period) {
        // Only check if late fine is enabled
        if (!contributionType.isLateFineEnabled() || contributionType.getLateFineAmount() == null) {
            return;
        }

        // Get the contribution record
        Contribution contribution = contributionRepository
                .findByContributionTypeIdAndMemberIdAndMonth(
                        contributionType.getId(), member.getId(), period)
                .orElse(null);
        
        if (contribution == null) {
            return;
        }

        // Skip if already paid or penalty already applied
        if (contribution.getStatus() == Contribution.ContributionStatus.PAID || contribution.isPenaltyApplied()) {
            return;
        }

        // Check if due date + grace period has passed
        LocalDate dueDate = contribution.getDueDate();
        if (dueDate == null) {
            return;
        }
        
        int gracePeriodDays = contributionType.getGracePeriodDays() != null ? contributionType.getGracePeriodDays() : 0;
        LocalDate penaltyDate = dueDate.plusDays(gracePeriodDays);

        if (today.isAfter(penaltyDate)) {
            // Mark contribution as overdue and set penalty amount
            contribution.setStatus(Contribution.ContributionStatus.OVERDUE);
            contribution.setPenaltyApplied(true);
            contribution.setPenalty(contributionType.getLateFineAmount());
            contributionRepository.save(contribution);

            // Impose late payment penalty via penalty service
            penaltyService.imposeLatePaymentPenalty(
                    tenant.getId(), 
                    member.getId(), 
                    contributionType.getId(), 
                    period, 
                    contributionType.getLateFineAmount());
            
            log.info("Imposed late payment penalty on member {} for {} period {}", 
                    member.getId(), contributionType.getName(), period);
        }
    }

    /**
     * Determine the current contribution period based on frequency
     */
    private YearMonth determineCurrentPeriod(ContributionType contributionType, YearMonth currentMonth) {
        switch (contributionType.getFrequency()) {
            case WEEKLY:
            case MONTHLY:
                return currentMonth;
            case QUARTERLY:
                // Return the start month of the current quarter
                int quarterStartMonth = ((currentMonth.getMonthValue() - 1) / 3) * 3 + 1;
                return YearMonth.of(currentMonth.getYear(), quarterStartMonth);
            case YEARLY:
                // Return January of the current year
                return YearMonth.of(currentMonth.getYear(), 1);
            default:
                return currentMonth;
        }
    }

    /**
     * Calculate the due date for a contribution period
     */
    private LocalDate calculateDueDate(ContributionType contributionType, YearMonth period) {
        int dueDay = contributionType.getDueDay() != null ? contributionType.getDueDay() : 1;
        
        // Ensure dueDay is valid for the month
        int maxDay = period.lengthOfMonth();
        if (dueDay > maxDay) {
            dueDay = maxDay;
        }
        
        return period.atDay(dueDay);
    }

    /**
     * Manual trigger for processing (for testing or admin use)
     */
    @Transactional
    public void runNow(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        processContributionsForTenant(tenant);
    }

    /**
     * Check and impose penalties for overdue contributions for a specific month
     * This allows admins to manually trigger penalty checks for any period
     */
    @Transactional
    public int checkAndImposePenaltiesForMonth(Long tenantId, YearMonth targetMonth) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        
        LocalDate today = LocalDate.now();
        int penaltiesImposed = 0;

        // Get active contribution types for this tenant
        List<ContributionType> activeTypes = contributionTypeRepository.findByTenantIdAndActiveTrue(tenantId);
        
        for (ContributionType contributionType : activeTypes) {
            // Skip if late fine not enabled
            if (!contributionType.isLateFineEnabled() || contributionType.getLateFineAmount() == null) {
                continue;
            }

            // Get all contributions for this type and month that are not fully paid and penalty not yet applied
            List<Contribution> overdueContributions = contributionRepository
                    .findByTenantIdAndContributionTypeIdAndMonth(tenantId, contributionType.getId(), targetMonth);
            
            for (Contribution contribution : overdueContributions) {
                // Skip if already paid or penalty already applied
                if (contribution.getStatus() == Contribution.ContributionStatus.PAID || contribution.isPenaltyApplied()) {
                    continue;
                }

                // Check if due date + grace period has passed
                LocalDate dueDate = contribution.getDueDate();
                if (dueDate == null) {
                    continue;
                }
                
                int gracePeriodDays = contributionType.getGracePeriodDays() != null ? contributionType.getGracePeriodDays() : 0;
                LocalDate penaltyDate = dueDate.plusDays(gracePeriodDays);

                if (today.isAfter(penaltyDate)) {
                    // Mark contribution as overdue and set penalty amount
                    contribution.setStatus(Contribution.ContributionStatus.OVERDUE);
                    contribution.setPenaltyApplied(true);
                    contribution.setPenalty(contributionType.getLateFineAmount());
                    contributionRepository.save(contribution);

                    // Impose late payment penalty via penalty service
                    penaltyService.imposeLatePaymentPenalty(
                            tenant.getId(), 
                            contribution.getMember().getId(), 
                            contributionType.getId(), 
                            targetMonth, 
                            contributionType.getLateFineAmount());
                    
                    penaltiesImposed++;
                    log.info("Imposed late payment penalty on member {} for {} period {}", 
                            contribution.getMember().getId(), contributionType.getName(), targetMonth);
                }
            }
        }

        return penaltiesImposed;
    }
}
