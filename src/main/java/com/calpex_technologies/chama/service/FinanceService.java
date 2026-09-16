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

    public Page<TransactionDto> getTransactionsPaginated(Long tenantId, int page, int size, String type, String direction, Long memberId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Transaction> transactionPage;
        
        boolean hasType = type != null && !type.isEmpty();
        boolean hasDirection = direction != null && !direction.isEmpty();
        boolean hasMember = memberId != null;
        
        // Build query based on filters - using specification pattern would be cleaner but this works
        if (hasMember) {
            if (hasType && hasDirection) {
                Transaction.TransactionType txType = Transaction.TransactionType.valueOf(type);
                Transaction.TransactionDirection txDirection = Transaction.TransactionDirection.valueOf(direction);
                transactionPage = transactionRepository.findByTenantIdAndMemberIdAndTypeAndDirection(tenantId, memberId, txType, txDirection, pageable);
            } else if (hasType) {
                Transaction.TransactionType txType = Transaction.TransactionType.valueOf(type);
                transactionPage = transactionRepository.findByTenantIdAndMemberIdAndType(tenantId, memberId, txType, pageable);
            } else if (hasDirection) {
                Transaction.TransactionDirection txDirection = Transaction.TransactionDirection.valueOf(direction);
                transactionPage = transactionRepository.findByTenantIdAndMemberIdAndDirection(tenantId, memberId, txDirection, pageable);
            } else {
                transactionPage = transactionRepository.findByTenantIdAndMemberIdOrderByDateDescIdDesc(tenantId, memberId, pageable);
            }
        } else {
            if (hasType && hasDirection) {
                Transaction.TransactionType txType = Transaction.TransactionType.valueOf(type);
                Transaction.TransactionDirection txDirection = Transaction.TransactionDirection.valueOf(direction);
                transactionPage = transactionRepository.findByTenantIdAndTypeAndDirection(tenantId, txType, txDirection, pageable);
            } else if (hasType) {
                Transaction.TransactionType txType = Transaction.TransactionType.valueOf(type);
                transactionPage = transactionRepository.findByTenantIdAndType(tenantId, txType, pageable);
            } else if (hasDirection) {
                Transaction.TransactionDirection txDirection = Transaction.TransactionDirection.valueOf(direction);
                transactionPage = transactionRepository.findByTenantIdAndDirection(tenantId, txDirection, pageable);
            } else {
                transactionPage = transactionRepository.findByTenantIdOrderByDateDescIdDesc(tenantId, pageable);
            }
        }
        
        return transactionPage.map(this::toDto);
    }

    public List<TransactionDto> getTransactionsByDateRange(Long tenantId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.findByTenantIdAndDateBetween(tenantId, startDate, endDate).stream()
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
