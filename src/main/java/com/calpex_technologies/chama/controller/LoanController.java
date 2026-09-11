package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.LoanDto;
import com.calpex_technologies.chama.dto.LoanPenaltyDto;
import com.calpex_technologies.chama.scheduler.LoanScheduler;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.LoanPenaltyService;
import com.calpex_technologies.chama.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/my-loans")
    public ResponseEntity<List<LoanDto>> getMyLoans(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getMyLoans(principal.getTenantId(), principal.getMemberId()));
    }

    @GetMapping("/pending-approvals")
    public ResponseEntity<List<LoanDto>> getPendingApprovals(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getPendingApprovals(principal.getTenantId(), principal.getMemberId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanDto> getLoan(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanService.getLoan(id, principal.getTenantId()));
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

    @PostMapping("/{id}/approve")
    public ResponseEntity<LoanDto> approveLoan(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String comments = body != null ? body.get("comments") : null;
        return ResponseEntity.ok(loanService.approveLoan(id, principal.getTenantId(), principal.getMemberId(), comments));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<LoanDto> rejectLoan(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String reason = body != null ? body.get("reason") : "No reason provided";
        return ResponseEntity.ok(loanService.rejectLoan(id, principal.getTenantId(), principal.getMemberId(), reason));
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
}
