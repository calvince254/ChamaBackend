package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.DashboardAnalyticsDto;
import com.calpex_technologies.chama.dto.DashboardAnalyticsDto.*;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final MemberRepository memberRepository;
    private final ContributionRepository contributionRepository;
    private final LoanRepository loanRepository;
    private final WelfareContributionRepository welfareContributionRepository;
    private final WelfareEventRepository welfareEventRepository;
    private final InvestmentRepository investmentRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAttendanceRepository attendanceRepository;
    private final MerryGoRoundRoundRepository roundRepository;
    private final ShareRepository shareRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public DashboardAnalyticsDto getAnalytics(Long tenantId) {
        // Get active members count
        int activeMembers = 0;
        try {
            List<Member> activeList = memberRepository.findByTenantIdAndStatus(tenantId, Member.MemberStatus.ACTIVE);
            activeMembers = activeList != null ? activeList.size() : 0;
        } catch (Exception e) {
            log.debug("Error counting active members: {}", e.getMessage());
        }
        
        // Calculate total savings (contributions)
        BigDecimal totalSavings = calculateTotalSavings(tenantId);
        
        // Calculate loans outstanding
        BigDecimal loansOutstanding = calculateLoansOutstanding(tenantId);
        int loansNeedingAttention = countLoansNeedingAttention(tenantId);
        
        // Calculate welfare balance
        BigDecimal welfareBalance = calculateWelfareBalance(tenantId);
        
        // Calculate investment value
        BigDecimal investmentValue = calculateInvestmentValue(tenantId);
        
        // Calculate fund allocation percentages
        BigDecimal totalFunds = totalSavings.add(investmentValue).add(welfareBalance).add(loansOutstanding);
        BigDecimal savingsPercent = BigDecimal.ZERO;
        BigDecimal investmentsPercent = BigDecimal.ZERO;
        BigDecimal welfarePercent = BigDecimal.ZERO;
        BigDecimal loansPercent = BigDecimal.ZERO;
        
        if (totalFunds.compareTo(BigDecimal.ZERO) > 0) {
            savingsPercent = totalSavings.multiply(BigDecimal.valueOf(100)).divide(totalFunds, 0, RoundingMode.HALF_UP);
            investmentsPercent = investmentValue.multiply(BigDecimal.valueOf(100)).divide(totalFunds, 0, RoundingMode.HALF_UP);
            welfarePercent = welfareBalance.multiply(BigDecimal.valueOf(100)).divide(totalFunds, 0, RoundingMode.HALF_UP);
            loansPercent = loansOutstanding.multiply(BigDecimal.valueOf(100)).divide(totalFunds, 0, RoundingMode.HALF_UP);
        }
        
        // Calculate ledger balance (total credits - total debits)
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalDebits = BigDecimal.ZERO;
        try {
            totalCredits = transactionRepository.sumTotalCreditsByTenantId(tenantId);
            totalDebits = transactionRepository.sumTotalDebitsByTenantId(tenantId);
        } catch (Exception e) {
            log.debug("Error calculating ledger balance: {}", e.getMessage());
        }
        BigDecimal ledgerBalance = (totalCredits != null ? totalCredits : BigDecimal.ZERO)
                .subtract(totalDebits != null ? totalDebits : BigDecimal.ZERO);
        
        // Get fund growth data (last 6 months)
        List<FundGrowthPoint> fundGrowth = calculateFundGrowth(tenantId);
        
        // Get next meeting
        MeetingInfo nextMeeting = getNextMeeting(tenantId, activeMembers);
        
        // Get loans needing attention
        List<LoanAlert> loanAlerts = getLoansNeedingAttention(tenantId);
        
        // Get next payout
        PayoutInfo nextPayout = getNextPayout(tenantId);
        
        // Get recent transactions
        List<TransactionInfo> recentTransactions = getRecentTransactions(tenantId);
        
        // Get pending approvals
        List<ApprovalInfo> pendingApprovals = getPendingApprovals(tenantId);
        
        return DashboardAnalyticsDto.builder()
                .totalSavings(totalSavings)
                .activeMembers(activeMembers)
                .loansOutstanding(loansOutstanding)
                .loansNeedingAttention(loansNeedingAttention)
                .welfareBalance(welfareBalance)
                .investmentValue(investmentValue)
                .ledgerBalance(ledgerBalance)
                .totalCredits(totalCredits != null ? totalCredits : BigDecimal.ZERO)
                .totalDebits(totalDebits != null ? totalDebits : BigDecimal.ZERO)
                .savingsPercent(savingsPercent)
                .investmentsPercent(investmentsPercent)
                .welfarePercent(welfarePercent)
                .loansPercent(loansPercent)
                .fundGrowth(fundGrowth)
                .nextMeeting(nextMeeting)
                .loanAlerts(loanAlerts)
                .nextPayout(nextPayout)
                .recentTransactions(recentTransactions)
                .pendingApprovals(pendingApprovals)
                .build();
    }
    
    private BigDecimal calculateTotalSavings(Long tenantId) {
        // Sum of all paid contributions
        BigDecimal contributions = BigDecimal.ZERO;
        try {
            contributions = contributionRepository.sumPaidAmountByTenantId(tenantId);
        } catch (Exception e) {
            log.debug("Error getting contributions: {}", e.getMessage());
        }
        
        // Add share capital
        BigDecimal shares = BigDecimal.ZERO;
        try {
            shares = shareRepository.sumTotalValueByTenantId(tenantId);
        } catch (Exception e) {
            log.debug("Could not get share value: {}", e.getMessage());
        }
        
        return (contributions != null ? contributions : BigDecimal.ZERO)
                .add(shares != null ? shares : BigDecimal.ZERO);
    }
    
    private BigDecimal calculateLoansOutstanding(Long tenantId) {
        try {
            BigDecimal outstanding = loanRepository.sumOutstandingBalanceByTenantId(tenantId);
            return outstanding != null ? outstanding : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
    
    private int countLoansNeedingAttention(Long tenantId) {
        try {
            return loanRepository.countOverdueByTenantId(tenantId, LocalDate.now());
        } catch (Exception e) {
            return 0;
        }
    }
    
    private BigDecimal calculateWelfareBalance(Long tenantId) {
        try {
            BigDecimal contributions = welfareContributionRepository.sumByTenantId(tenantId);
            BigDecimal disbursements = welfareEventRepository.sumDisbursementsByTenantId(tenantId);
            return (contributions != null ? contributions : BigDecimal.ZERO)
                    .subtract(disbursements != null ? disbursements : BigDecimal.ZERO);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
    
    private BigDecimal calculateInvestmentValue(Long tenantId) {
        try {
            BigDecimal value = investmentRepository.sumCurrentValueByTenantId(tenantId);
            return value != null ? value : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
    
    private List<FundGrowthPoint> calculateFundGrowth(Long tenantId) {
        List<FundGrowthPoint> growth = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM");
        
        YearMonth currentMonth = YearMonth.now();
        BigDecimal runningTotal = BigDecimal.ZERO;
        
        // Get data for last 6 months
        for (int i = 5; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            LocalDate startOfMonth = month.atDay(1);
            LocalDate endOfMonth = month.atEndOfMonth();
            
            try {
                // Sum contributions for this month
                BigDecimal monthContributions = contributionRepository.sumPaidAmountByTenantIdAndDateRange(
                        tenantId, startOfMonth, endOfMonth);
                
                if (monthContributions != null) {
                    runningTotal = runningTotal.add(monthContributions);
                }
            } catch (Exception e) {
                // Ignore errors, use running total
            }
            
            growth.add(FundGrowthPoint.builder()
                    .month(month.format(formatter))
                    .amount(runningTotal)
                    .build());
        }
        
        return growth;
    }
    
    private MeetingInfo getNextMeeting(Long tenantId, int activeMembers) {
        try {
            return meetingRepository.findNextMeeting(tenantId, LocalDate.now())
                    .map(meeting -> {
                        int attended = 0;
                        try {
                            attended = attendanceRepository.countByMeetingIdAndAttended(meeting.getId(), true);
                        } catch (Exception e) {
                            // Ignore
                        }
                        
                        return MeetingInfo.builder()
                                .id(meeting.getId())
                                .dateTime(meeting.getDate().atTime(java.time.LocalTime.of(9, 0)))
                                .venue(meeting.getVenue())
                                .type(meeting.getType().name())
                                .status(meeting.getMinutesStatus().name())
                                .attended(attended)
                                .totalMembers(activeMembers)
                                .build();
                    })
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Error getting next meeting: {}", e.getMessage());
            return null;
        }
    }
    
    private List<LoanAlert> getLoansNeedingAttention(Long tenantId) {
        try {
            LocalDate today = LocalDate.now();
            return loanRepository.findOverdueByTenantId(tenantId, today).stream()
                    .limit(5)
                    .map(loan -> LoanAlert.builder()
                            .id(loan.getId())
                            .memberName(loan.getMember().getName())
                            .loanType(loan.getLoanProduct() != null ? loan.getLoanProduct().getName() : "Loan")
                            .amount(loan.getPrincipal())
                            .overdue(loan.getBalanceOutstanding() != null ? loan.getBalanceOutstanding() : BigDecimal.ZERO)
                            .daysOverdue((int) java.time.temporal.ChronoUnit.DAYS.between(
                                    loan.getNextDueDate() != null ? loan.getNextDueDate() : today, today))
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    private PayoutInfo getNextPayout(Long tenantId) {
        try {
            return roundRepository.findNextPayout(tenantId, LocalDate.now())
                    .map(round -> {
                        // Get participant count from the cycle
                        int totalCount = 0;
                        try {
                            if (round.getCycle() != null && round.getCycle().getParticipants() != null) {
                                totalCount = round.getCycle().getParticipants().size();
                            }
                        } catch (Exception e) {
                            // Ignore lazy loading issues
                        }
                        
                        BigDecimal paidAmount = round.getCollectedAmount() != null 
                                ? round.getCollectedAmount() : BigDecimal.ZERO;
                        BigDecimal totalAmount = round.getExpectedAmount() != null 
                                ? round.getExpectedAmount() : BigDecimal.ZERO;
                        
                        // Estimate paid count based on amounts
                        int paidCount = 0;
                        try {
                            if (round.getCycle() != null && round.getCycle().getContributionAmount() != null 
                                    && round.getCycle().getContributionAmount().compareTo(BigDecimal.ZERO) > 0) {
                                paidCount = paidAmount.divide(round.getCycle().getContributionAmount(), 0, RoundingMode.DOWN).intValue();
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                        
                        return PayoutInfo.builder()
                                .id(round.getId())
                                .memberName(round.getRecipient() != null ? round.getRecipient().getName() : "TBD")
                                .roundNumber(round.getPosition())
                                .dueDate(round.getPayoutDate())
                                .paidCount(paidCount)
                                .totalCount(totalCount)
                                .paidAmount(paidAmount)
                                .totalAmount(totalAmount)
                                .build();
                    })
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Error getting next payout: {}", e.getMessage());
            return null;
        }
    }
    
    private List<TransactionInfo> getRecentTransactions(Long tenantId) {
        List<TransactionInfo> transactions = new ArrayList<>();
        
        try {
            // Fetch from the Transaction (GL) table like FinanceService does
            transactionRepository.findByTenantIdOrderByDateDescIdDesc(tenantId).stream()
                    .limit(10)
                    .forEach(t -> {
                        transactions.add(TransactionInfo.builder()
                                .id(t.getId())
                                .date(t.getDate())
                                .type(t.getType().name())
                                .description(t.getDescription())
                                .amount(t.getAmount())
                                .credit(t.getDirection() == Transaction.TransactionDirection.IN)
                                .build());
                    });
        } catch (Exception e) {
            log.debug("Error getting recent transactions: {}", e.getMessage());
        }
        
        return transactions;
    }
    
    private List<ApprovalInfo> getPendingApprovals(Long tenantId) {
        List<ApprovalInfo> approvals = new ArrayList<>();
        
        try {
            // Get pending loans
            loanRepository.findPendingByTenantId(tenantId).forEach(loan -> {
                approvals.add(ApprovalInfo.builder()
                        .id(loan.getId())
                        .type("LOAN")
                        .title(loan.getLoanProduct() != null ? loan.getLoanProduct().getName() : "Loan")
                        .memberName(loan.getMember().getName())
                        .amount(loan.getPrincipal())
                        .status(loan.getStatus().name())
                        .currentLevel(loan.getCurrentApprovalLevel() != null ? loan.getCurrentApprovalLevel() : 1)
                        .build());
            });
        } catch (Exception e) {
            // Ignore
        }
        
        try {
            // Get pending investments
            investmentRepository.findPendingByTenantId(tenantId).forEach(inv -> {
                approvals.add(ApprovalInfo.builder()
                        .id(inv.getId())
                        .type("INVESTMENT")
                        .title(inv.getName())
                        .memberName("")
                        .amount(inv.getAmountInvested())
                        .status(inv.getStatus().name())
                        .currentLevel(1)
                        .build());
            });
        } catch (Exception e) {
            // Ignore
        }
        
        return approvals.stream().limit(5).collect(Collectors.toList());
    }
}
