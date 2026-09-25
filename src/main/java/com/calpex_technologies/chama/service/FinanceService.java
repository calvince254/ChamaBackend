package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.FinanceSummaryDto;
import com.calpex_technologies.chama.dto.TransactionDto;
import com.calpex_technologies.chama.entity.Transaction;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinanceService {

    private final TransactionRepository transactionRepository;
    private final ContributionRepository contributionRepository;
    private final LoanRepository loanRepository;
    private final WelfareEventRepository welfareEventRepository;
    private final InvestmentRepository investmentRepository;

    public FinanceSummaryDto getFinanceSummary(Long tenantId) {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now();

        BigDecimal totalSavings = contributionRepository.sumAmountPaidByTenantId(tenantId);
        BigDecimal totalLoansOutstanding = loanRepository.sumOutstandingByTenantId(tenantId);
        BigDecimal welfareFundBalance = welfareEventRepository.sumCollectedByTenantId(tenantId);
        BigDecimal investmentValue = investmentRepository.sumCurrentValueByTenantId(tenantId);
        BigDecimal incomeThisMonth = transactionRepository.sumIncomeByTenantIdAndDateBetween(tenantId, startOfMonth, endOfMonth);
        BigDecimal expenditureThisMonth = transactionRepository.sumExpenseByTenantIdAndDateBetween(tenantId, startOfMonth, endOfMonth);

        return FinanceSummaryDto.builder()
                .totalSavings(totalSavings != null ? totalSavings : BigDecimal.ZERO)
                .totalLoansOutstanding(totalLoansOutstanding != null ? totalLoansOutstanding : BigDecimal.ZERO)
                .welfareFundBalance(welfareFundBalance != null ? welfareFundBalance : BigDecimal.ZERO)
                .investmentValue(investmentValue != null ? investmentValue : BigDecimal.ZERO)
                .incomeThisMonth(incomeThisMonth != null ? incomeThisMonth : BigDecimal.ZERO)
                .expenditureThisMonth(expenditureThisMonth != null ? expenditureThisMonth : BigDecimal.ZERO)
                .build();
    }

    public List<TransactionDto> getTransactions(Long tenantId) {
        return transactionRepository.findByTenantIdOrderByDateDescIdDesc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Page<TransactionDto> getTransactionsPaginated(Long tenantId, int page, int size, String type, String direction, Long memberId, LocalDate startDate, LocalDate endDate) {
        Pageable pageable = PageRequest.of(page, size);
        
        // Get filtered list first, then paginate
        List<TransactionDto> allFiltered = getTransactionsFiltered(tenantId, startDate, endDate, type, direction, memberId);
        
        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allFiltered.size());
        
        List<TransactionDto> pageContent = start >= allFiltered.size() ? 
                java.util.Collections.emptyList() : 
                allFiltered.subList(start, end);
        
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, allFiltered.size());
    }
    
    // Legacy method for backwards compatibility
    public Page<TransactionDto> getTransactionsPaginated(Long tenantId, int page, int size, String type, String direction, Long memberId) {
        return getTransactionsPaginated(tenantId, page, size, type, direction, memberId, null, null);
    }

    public List<TransactionDto> getTransactionsByDateRange(Long tenantId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.findByTenantIdAndDateBetween(tenantId, startDate, endDate).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get transactions with all filters applied (for PDF report generation).
     */
    public List<TransactionDto> getTransactionsFiltered(Long tenantId, LocalDate startDate, LocalDate endDate,
                                                         String type, String direction, Long memberId) {
        List<Transaction> allTransactions;
        
        // Start with date range or all
        if (startDate != null && endDate != null) {
            allTransactions = transactionRepository.findByTenantIdAndDateBetween(tenantId, startDate, endDate);
        } else if (startDate != null) {
            allTransactions = transactionRepository.findByTenantIdAndDateGreaterThanEqual(tenantId, startDate);
        } else if (endDate != null) {
            allTransactions = transactionRepository.findByTenantIdAndDateLessThanEqual(tenantId, endDate);
        } else {
            allTransactions = transactionRepository.findByTenantIdOrderByDateDescIdDesc(tenantId);
        }
        
        // Apply filters in memory (could be optimized with dynamic queries if needed)
        return allTransactions.stream()
                .filter(t -> type == null || type.isEmpty() || t.getType().name().equals(type))
                .filter(t -> direction == null || direction.isEmpty() || t.getDirection().name().equals(direction))
                .filter(t -> memberId == null || (t.getMember() != null && t.getMember().getId().equals(memberId)))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private TransactionDto toDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .date(transaction.getDate().toString())
                .type(transaction.getType().name())
                .description(transaction.getDescription())
                .amount(transaction.getAmount())
                .direction(transaction.getDirection().name())
                .memberName(transaction.getMember() != null ? transaction.getMember().getName() : null)
                .memberId(transaction.getMember() != null ? transaction.getMember().getId() : null)
                .build();
    }
}
