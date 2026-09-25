package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.FinanceSummaryDto;
import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.TransactionDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.FinanceReportPdfService;
import com.calpex_technologies.chama.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final FinanceReportPdfService financeReportPdfService;

    @GetMapping("/summary")
    public ResponseEntity<FinanceSummaryDto> getFinanceSummary(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(financeService.getFinanceSummary(principal.getTenantId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionDto>> getTransactions(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(financeService.getTransactions(principal.getTenantId()));
    }

    @GetMapping("/transactions/paginated")
    public ResponseEntity<PageResponse<TransactionDto>> getTransactionsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<TransactionDto> result = financeService.getTransactionsPaginated(
                principal.getTenantId(), page, size, type, direction, memberId, startDate, endDate);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @GetMapping("/transactions/range")
    public ResponseEntity<List<TransactionDto>> getTransactionsByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(financeService.getTransactionsByDateRange(principal.getTenantId(), startDate, endDate));
    }

    /**
     * Generate and download a PDF finance report with optional filters.
     */
    @GetMapping("/report/pdf")
    public ResponseEntity<byte[]> downloadFinanceReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Long memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        byte[] pdfContent = financeReportPdfService.generateFinanceReportPdf(
                principal.getTenantId(), startDate, endDate, type, direction, memberId);
        
        // Generate filename
        String filename = "Finance_Report";
        if (startDate != null && endDate != null) {
            filename += "_" + startDate + "_to_" + endDate;
        } else if (startDate != null) {
            filename += "_from_" + startDate;
        } else if (endDate != null) {
            filename += "_until_" + endDate;
        }
        filename += ".pdf";
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfContent);
    }
}
