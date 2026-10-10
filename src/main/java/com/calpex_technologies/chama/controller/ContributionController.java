package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.ContributionDto;
import com.calpex_technologies.chama.dto.ContributionReportItemDto;
import com.calpex_technologies.chama.dto.GenerateContributionsRequest;
import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.RecordPaymentRequest;
import com.calpex_technologies.chama.scheduler.ContributionScheduler;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.ContributionReportPdfService;
import com.calpex_technologies.chama.service.ContributionService;
import com.calpex_technologies.chama.service.ContributionTransactionsPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contributions")
@RequiredArgsConstructor
public class ContributionController {

    private final ContributionService contributionService;
    private final ContributionScheduler contributionScheduler;
    private final ContributionReportPdfService contributionReportPdfService;
    private final ContributionTransactionsPdfService contributionTransactionsPdfService;

    // ==================== REPORT ENDPOINTS ====================
    
    /**
     * Get contribution report with filters and pagination.
     * Filters: startDate, endDate, memberId, contributionTypeId
     */
    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('FULL_ACCESS', 'VIEW_CONTRIBUTION_REPORTS')")
    public ResponseEntity<PageResponse<ContributionDto>> getContributionReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long contributionTypeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : null;
        
        PageResponse<ContributionDto> response = contributionService.getContributionReport(
                principal.getTenantId(), start, end, memberId, contributionTypeId, page, size);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get contribution report summary totals.
     * Returns totalDue, totalPaid, and balance for the filtered contributions.
     */
    @GetMapping("/reports/summary")
    @PreAuthorize("hasAnyAuthority('FULL_ACCESS', 'VIEW_CONTRIBUTION_REPORTS')")
    public ResponseEntity<Map<String, Object>> getContributionReportSummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long contributionTypeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : null;
        
        Map<String, Object> summary = contributionService.getContributionReportSummary(
                principal.getTenantId(), start, end, memberId, contributionTypeId);
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Download contribution report as PDF.
     */
    @GetMapping("/reports/pdf")
    @PreAuthorize("hasAnyAuthority('FULL_ACCESS', 'DOWNLOAD_CONTRIBUTION_REPORT')")
    public ResponseEntity<byte[]> downloadContributionReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long contributionTypeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : null;
        
        byte[] pdfContent = contributionReportPdfService.generateContributionReportPdf(
                principal.getTenantId(), start, end, memberId, contributionTypeId);
        
        // Generate filename
        String filename = "Contribution_Report";
        if (start != null && end != null) {
            filename += "_" + start + "_to_" + end;
        } else if (start != null) {
            filename += "_from_" + start;
        } else if (end != null) {
            filename += "_until_" + end;
        }
        filename += ".pdf";
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfContent);
    }
    
    // ==================== CONTRIBUTION-BASED REPORT ENDPOINTS ====================
    
    /**
     * Get contributions report with pagination.
     * Shows contributions directly (including PENDING ones).
     * Supports optional contributionTypeId for "All Types" filter.
     * Supports optional status filter: PENDING, PAID, PARTIAL, OVERDUE, DEFAULTED, or ALL (default).
     */
    @GetMapping("/reports/transactions")
    @PreAuthorize("hasAnyAuthority('FULL_ACCESS', 'VIEW_CONTRIBUTION_REPORTS')")
    public ResponseEntity<PageResponse<ContributionReportItemDto>> getContributionsReport(
            @RequestParam(required = false) Long contributionTypeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : null;
        
        PageResponse<ContributionReportItemDto> response = contributionService.getContributionsReportWithStatus(
                principal.getTenantId(), contributionTypeId, memberId, start, end, status, page, size);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get contributions report summary.
     * Returns totalDue, totalPaid, balance, and count.
     * Supports optional contributionTypeId for "All Types" filter.
     * Supports optional status filter: PENDING, PAID, PARTIAL, OVERDUE, DEFAULTED, or ALL (default).
     */
    @GetMapping("/reports/transactions/summary")
    @PreAuthorize("hasAnyAuthority('FULL_ACCESS', 'VIEW_CONTRIBUTION_REPORTS')")
    public ResponseEntity<Map<String, Object>> getContributionsReportSummary(
            @RequestParam(required = false) Long contributionTypeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : null;
        
        Map<String, Object> summary = contributionService.getContributionsReportSummaryWithStatus(
                principal.getTenantId(), contributionTypeId, memberId, start, end, status);
        return ResponseEntity.ok(summary);
    }

    /**
     * Download contributions report as PDF.
     * Uses the same filters as the UI report.
     * Supports optional status filter: PENDING, PAID, PARTIAL, OVERDUE, DEFAULTED, or ALL (default).
     */
    @GetMapping("/reports/transactions/pdf")
    @PreAuthorize("hasAnyAuthority('FULL_ACCESS', 'DOWNLOAD_CONTRIBUTION_REPORT')")
    public ResponseEntity<byte[]> downloadContributionsReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long contributionTypeId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : null;
        
        byte[] pdfContent = contributionTransactionsPdfService.generateContributionsReportPdf(
                principal.getTenantId(), start, end, memberId, contributionTypeId, status);
        
        // Generate filename
        String filename = "Contributions_Report";
        if (start != null && end != null) {
            filename += "_" + start + "_to_" + end;
        } else if (start != null) {
            filename += "_from_" + start;
        } else if (end != null) {
            filename += "_until_" + end;
        }
        filename += ".pdf";
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfContent);
    }

    // ==================== EXISTING ENDPOINTS ====================

    // ==================== EXISTING ENDPOINTS ====================

    @GetMapping
    public ResponseEntity<List<ContributionDto>> getAllContributions(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long contributionTypeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        if (month != null && contributionTypeId != null) {
            YearMonth ym = YearMonth.parse(month);
            // Return only saved contributions (not virtual pending records)
            return ResponseEntity.ok(contributionService.getSavedContributionsByTypeAndMonth(
                    principal.getTenantId(), contributionTypeId, ym));
        } else if (month != null) {
            YearMonth ym = YearMonth.parse(month);
            return ResponseEntity.ok(contributionService.getContributionsByMonth(principal.getTenantId(), ym));
        }
        return ResponseEntity.ok(contributionService.getAllContributions(principal.getTenantId()));
    }
    
    /**
     * Get contributions with pagination by type and month.
     * Default: 10 items per page.
     */
    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<ContributionDto>> getContributionsPaginated(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Long contributionTypeId,
            @RequestParam String month,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        YearMonth ym = YearMonth.parse(month);
        PageResponse<ContributionDto> response = contributionService.getContributionsPaginated(
                principal.getTenantId(), contributionTypeId, ym, status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContributionDto> getContribution(
            @PathVariable Long id, 
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionService.getContribution(id, principal.getTenantId()));
    }

    @PostMapping
    public ResponseEntity<ContributionDto> createContribution(
            @RequestBody ContributionDto dto, 
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionService.createContribution(dto, principal.getTenantId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContributionDto> updateContribution(
            @PathVariable Long id, 
            @RequestBody ContributionDto dto, 
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionService.updateContribution(id, dto, principal.getTenantId()));
    }

    /**
     * Record a payment for a contribution (supports partial payments)
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<ContributionDto> recordPayment(
            @PathVariable Long id,
            @RequestBody RecordPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionService.recordPayment(id, request.getAmount(), principal.getTenantId()));
    }

    /**
     * Generate contribution records for selected members (or all if memberIds empty) for a specific contribution type and month
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateContributions(
            @RequestBody GenerateContributionsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        YearMonth ym = YearMonth.parse(request.getMonth());
        List<ContributionDto> created = contributionService.generateContributionsForMembers(
                principal.getTenantId(), request.getContributionTypeId(), ym, request.getMemberIds());
        
        Map<String, Object> response = new HashMap<>();
        response.put("created", created.size());
        response.put("contributions", created);
        response.put("message", created.size() + " contribution records generated");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get members available for generating contributions (active members without contribution for this type/month)
     */
    @GetMapping("/available-members")
    public ResponseEntity<List<Map<String, Object>>> getAvailableMembers(
            @RequestParam Long contributionTypeId,
            @RequestParam String month,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        YearMonth ym = YearMonth.parse(month);
        return ResponseEntity.ok(contributionService.getMembersWithoutContribution(
                principal.getTenantId(), contributionTypeId, ym));
    }

    /**
     * Generate contribution records for ALL active contribution types for a month
     */
    @PostMapping("/generate-all")
    public ResponseEntity<Map<String, Object>> generateAllContributions(
            @RequestParam String month,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        YearMonth ym = YearMonth.parse(month);
        int created = contributionService.generateAllMonthlyContributions(principal.getTenantId(), ym);
        
        Map<String, Object> response = new HashMap<>();
        response.put("created", created);
        response.put("message", created + " contribution records generated for all active types");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Check and impose penalties for overdue contributions for a specific month
     */
    @PostMapping("/check-penalties")
    public ResponseEntity<Map<String, Object>> checkAndImposePenalties(
            @RequestParam String month,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        YearMonth ym = YearMonth.parse(month);
        int penaltiesImposed = contributionScheduler.checkAndImposePenaltiesForMonth(principal.getTenantId(), ym);
        
        Map<String, Object> response = new HashMap<>();
        response.put("penaltiesImposed", penaltiesImposed);
        response.put("message", penaltiesImposed + " late payment penalties imposed");
        
        return ResponseEntity.ok(response);
    }
}
