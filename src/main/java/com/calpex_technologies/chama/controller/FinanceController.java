package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.FinanceSummaryDto;
import com.calpex_technologies.chama.dto.TransactionDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping("/summary")
    public ResponseEntity<FinanceSummaryDto> getFinanceSummary(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(financeService.getFinanceSummary(principal.getTenantId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionDto>> getTransactions(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(financeService.getTransactions(principal.getTenantId()));
    }

    @GetMapping("/transactions/range")
    public ResponseEntity<List<TransactionDto>> getTransactionsByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(financeService.getTransactionsByDateRange(principal.getTenantId(), startDate, endDate));
    }
}
