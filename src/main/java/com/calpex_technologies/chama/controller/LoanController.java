package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.LoanDto;
import com.calpex_technologies.chama.dto.LoanEligibilityDto;
import com.calpex_technologies.chama.dto.LoanIncomeSummaryDto;
import com.calpex_technologies.chama.dto.LoanPaymentDto;
import com.calpex_technologies.chama.dto.LoanPenaltyDto;
import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.RecordPaymentRequest;
import com.calpex_technologies.chama.dto.TransactionDto;
import com.calpex_technologies.chama.scheduler.LoanScheduler;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.LoanPenaltyService;
import com.calpex_technologies.chama.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;
    private final LoanPenaltyService penaltyService;
    private final LoanScheduler loanScheduler;

    // ==================== LOAN CRUD ====================

    @GetMapping
    public ResponseEntity<List<LoanDto>> getAllLoans(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getAllLoans(principal.getTenantId()));
    }
    
    /**
     * Get all loans with pagination.
     * Default: 10 items per page.
     */
    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<LoanDto>> getAllLoansPaginated(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<LoanDto> response = loanService.getAllLoansPaginated(
                principal.getTenantId(), status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-loans")
    public ResponseEntity<List<LoanDto>> getMyLoans(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getMyLoans(principal.getTenantId(), principal.getMemberId()));
    }
    
    /**
     * Get my loans with pagination.
     * Default: 10 items per page.
     */
    @GetMapping("/my-loans/paginated")
    public ResponseEntity<PageResponse<LoanDto>> getMyLoansPaginated(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<LoanDto> response = loanService.getMyLoansPaginated(
                principal.getTenantId(), principal.getMemberId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending-approvals")
    public ResponseEntity<List<LoanDto>> getPendingApprovals(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getPendingApprovals(principal.getTenantId(), principal.getMemberId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanDto> getLoan(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getLoan(id, principal.getTenantId()));
    }

    // ==================== LOAN ELIGIBILITY CHECK ====================

    /**
     * Get current member's credit score without requiring loan amount.
     * This is useful for displaying credit score info on the loans page.
     */
    @GetMapping("/my-credit-score")
    public ResponseEntity<LoanEligibilityDto.CreditScoreResponse> getMyCreditScore(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getMemberCreditScore(principal.getTenantId(), principal.getMemberId()));
    }

    /**
     * Check loan eligibility based on contributions.
     * Rule: Applicant's contributions + guarantors' contributions must be >= loan amount
     */
    @PostMapping("/check-eligibility")
    public ResponseEntity<LoanEligibilityDto> checkLoanEligibility(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long memberId = request.get("memberId") != null 
                ? Long.valueOf(request.get("memberId").toString()) 
                : principal.getMemberId();
        
        @SuppressWarnings("unchecked")
        List<Number> guarantorIdNumbers = (List<Number>) request.get("guarantorIds");
        List<Long> guarantorIds = guarantorIdNumbers != null 
                ? guarantorIdNumbers.stream().map(Number::longValue).toList()
                : List.of();
        
        BigDecimal loanAmount = new BigDecimal(request.get("loanAmount").toString());
        
        return ResponseEntity.ok(loanService.checkLoanEligibility(
                principal.getTenantId(), memberId, guarantorIds, loanAmount));
    }

    // ==================== LOAN APPLICATION ====================

    @PostMapping("/apply")
    public ResponseEntity<LoanDto> applyForLoan(
            @RequestBody LoanDto dto,
            @RequestParam(defaultValue = "false") boolean isAdmin,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.applyForLoan(dto, principal.getTenantId(), principal.getMemberId(), isAdmin));
    }

    // Legacy endpoint for backward compatibility
    @PostMapping
    public ResponseEntity<LoanDto> createLoan(
            @RequestBody LoanDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Default to admin applying on behalf (old behavior)
        return ResponseEntity.ok(loanService.applyForLoan(dto, principal.getTenantId(), principal.getMemberId(), true));
    }

    // ==================== APPROVAL WORKFLOW ====================

    /**
     * Approve loan as a guarantor (Phase 1)
     */
    @PostMapping("/{id}/approve-guarantor")
    public ResponseEntity<LoanDto> approveAsGuarantor(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String comments = body != null ? body.get("comments") : null;
        return ResponseEntity.ok(loanService.approveAsGuarantor(id, principal.getTenantId(), principal.getMemberId(), comments));
    }

    /**
     * Approve loan as an approver (Phase 2)
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<LoanDto> approveLoan(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String comments = body != null ? body.get("comments") : null;
        return ResponseEntity.ok(loanService.approveLoan(id, principal.getTenantId(), principal.getMemberId(), comments));
    }

    /**
     * Reject loan (as guarantor or approver based on loan status)
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<LoanDto> rejectLoan(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String reason = body != null ? body.get("reason") : "No reason provided";
        boolean asGuarantor = body != null && "true".equalsIgnoreCase(body.get("asGuarantor"));
        return ResponseEntity.ok(loanService.rejectLoan(id, principal.getTenantId(), principal.getMemberId(), reason, asGuarantor));
    }

    @PostMapping("/{id}/disburse")
    public ResponseEntity<LoanDto> disburseLoan(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.disburseLoan(id, principal.getTenantId()));
    }

    // ==================== REPAYMENT ====================

    @PostMapping("/{id}/repay")
    public ResponseEntity<LoanDto> makeRepayment(
            @PathVariable Long id,
            @RequestParam BigDecimal amount,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.makeRepayment(id, amount, principal.getTenantId()));
    }

    // ==================== PAYMENT VERIFICATION ====================

    /**
     * Record a payment for verification (user submits payment).
     */
    @PostMapping("/{id}/payments/record")
    public ResponseEntity<LoanPaymentDto> recordPayment(
            @PathVariable Long id,
            @RequestBody RecordPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.recordPaymentForVerification(
                id,
                request.getAmount(),
                request.getPaymentMethod(),
                request.getTransactionReference(),
                request.getNotes(),
                principal.getTenantId(),
                principal.getMemberId()
        ));
    }

    /**
     * Get payment history for a specific loan.
     */
    @GetMapping("/{id}/payments")
    public ResponseEntity<List<LoanPaymentDto>> getPaymentsForLoan(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getPaymentsForLoan(id, principal.getTenantId()));
    }

    /**
     * Get my payments (member's own payment history).
     */
    @GetMapping("/payments/my-payments")
    public ResponseEntity<List<LoanPaymentDto>> getMyPayments(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getMyPayments(principal.getMemberId()));
    }

    // ==================== PENALTIES ====================

    @GetMapping("/penalties")
    public ResponseEntity<List<LoanPenaltyDto>> getAllPenalties(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(penaltyService.getAllPenalties(principal.getTenantId()));
    }

    @GetMapping("/penalties/my-penalties")
    public ResponseEntity<List<LoanPenaltyDto>> getMyPenalties(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(penaltyService.getMyPenalties(principal.getTenantId(), principal.getMemberId()));
    }

    @GetMapping("/{loanId}/penalties")
    public ResponseEntity<List<LoanPenaltyDto>> getPenaltiesForLoan(
            @PathVariable Long loanId,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Verify loan belongs to tenant
        loanService.getLoan(loanId, principal.getTenantId());
        return ResponseEntity.ok(penaltyService.getPenaltiesForLoan(loanId));
    }

    @GetMapping("/penalties/{id}")
    public ResponseEntity<LoanPenaltyDto> getPenalty(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(penaltyService.getPenalty(id, principal.getTenantId()));
    }

    @PostMapping("/penalties/{id}/waive")
    public ResponseEntity<LoanPenaltyDto> waivePenalty(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String reason = body != null ? body.get("reason") : "No reason provided";
        return ResponseEntity.ok(penaltyService.waivePenalty(id, principal.getTenantId(), principal.getMemberId(), reason));
    }

    @PostMapping("/penalties/{id}/pay")
    public ResponseEntity<LoanPenaltyDto> payPenalty(
            @PathVariable Long id,
            @RequestParam BigDecimal amount,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(penaltyService.recordPayment(id, principal.getTenantId(), amount));
    }

    @GetMapping("/penalties/summary")
    public ResponseEntity<LoanPenaltyService.PenaltySummary> getPenaltySummary(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(penaltyService.getPenaltySummary(principal.getTenantId()));
    }

    // ==================== ADMIN: MANUAL PENALTY PROCESSING ====================

    @PostMapping("/process-overdue")
    public ResponseEntity<Map<String, Object>> processOverdueLoans(@AuthenticationPrincipal UserPrincipal principal) {
        int[] result = loanScheduler.processOverdueLoansForTenant(principal.getTenantId());
        return ResponseEntity.ok(Map.of(
                "loansProcessed", result[0],
                "penaltiesApplied", result[1],
                "message", "Overdue loan processing completed"
        ));
    }
    
    // ==================== LOAN INCOME ====================
    
    @GetMapping("/income/summary")
    public ResponseEntity<LoanIncomeSummaryDto> getLoanIncomeSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getLoanIncomeSummary(principal.getTenantId(), startDate, endDate));
    }
    
    @GetMapping("/income/transactions")
    @Transactional(readOnly = true)
    public ResponseEntity<PageResponse<TransactionDto>> getLoanIncomeTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        var transactions = loanService.getLoanIncomeTransactions(
                principal.getTenantId(), 
                PageRequest.of(page, size)
        );
        
        List<TransactionDto> dtos = transactions.getContent().stream()
                .map(t -> TransactionDto.builder()
                        .id(t.getId())
                        .date(t.getDate().toString())
                        .type(t.getType().name())
                        .description(t.getDescription())
                        .amount(t.getAmount())
                        .direction(t.getDirection().name())
                        .memberName(t.getMember() != null ? t.getMember().getName() : null)
                        .referenceId(t.getReferenceId())
                        .referenceType(t.getReferenceType())
                        .build())
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(PageResponse.<TransactionDto>builder()
                .content(dtos)
                .totalElements(transactions.getTotalElements())
                .totalPages(transactions.getTotalPages())
                .size(transactions.getSize())
                .page(transactions.getNumber())
                .first(transactions.isFirst())
                .last(transactions.isLast())
                .build());
    }
}
