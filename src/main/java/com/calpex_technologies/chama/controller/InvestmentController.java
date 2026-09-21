package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.InvestmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;

    @GetMapping
    public ResponseEntity<List<InvestmentDto>> getAllInvestments(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.getAllInvestments(principal.getTenantId()));
    }

    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<InvestmentDto>> getInvestmentsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approvalStatus,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<InvestmentDto> result = investmentService.getInvestmentsPaginated(
                principal.getTenantId(), page, size, status, approvalStatus);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvestmentDto> getInvestment(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.getInvestment(id, principal.getTenantId()));
    }

    @PostMapping
    public ResponseEntity<InvestmentDto> createInvestment(@RequestBody InvestmentDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.createInvestment(dto, principal.getTenantId(), principal.getMemberId()));
    }

    // Submit individual approval vote
    @PostMapping("/{id}/vote")
    public ResponseEntity<InvestmentDto> submitApprovalVote(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean approve = Boolean.TRUE.equals(request.get("approve"));
        String comments = (String) request.get("comments");
        return ResponseEntity.ok(investmentService.submitApproval(id, principal.getTenantId(), principal.getMemberId(), approve, comments));
    }

    // Get pending approvals for current user
    @GetMapping("/my-pending-approvals")
    public ResponseEntity<List<InvestmentApprovalDto>> getMyPendingApprovals(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.getPendingApprovalsForMember(principal.getMemberId()));
    }

    // Count pending approvals for badge
    @GetMapping("/my-pending-approvals/count")
    public ResponseEntity<Map<String, Long>> countMyPendingApprovals(@AuthenticationPrincipal UserPrincipal principal) {
        long count = investmentService.countPendingApprovalsForMember(principal.getMemberId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    // Get all approvals for a specific investment
    @GetMapping("/{id}/approvals")
    public ResponseEntity<List<InvestmentApprovalDto>> getInvestmentApprovals(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.getApprovalsForInvestment(id, principal.getTenantId()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<InvestmentDto> approveInvestment(
            @PathVariable Long id, 
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.approveInvestment(id, principal.getTenantId(), principal.getEmail()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<InvestmentDto> rejectInvestment(
            @PathVariable Long id, 
            @RequestBody(required = false) ApproveRejectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(investmentService.rejectInvestment(id, principal.getTenantId(), reason));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InvestmentDto> updateInvestment(@PathVariable Long id, @RequestBody InvestmentDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.updateInvestment(id, dto, principal.getTenantId()));
    }

    @PostMapping("/{id}/value")
    public ResponseEntity<InvestmentDto> updateInvestmentValue(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInvestmentValueRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.updateInvestmentValue(id, principal.getTenantId(), request));
    }

    @PostMapping("/{id}/sell")
    public ResponseEntity<InvestmentDto> sellInvestment(
            @PathVariable Long id,
            @Valid @RequestBody SellInvestmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.sellInvestment(id, principal.getTenantId(), request));
    }

    @PostMapping("/{id}/write-off")
    public ResponseEntity<InvestmentDto> writeOffInvestment(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveRejectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String notes = request != null ? request.getReason() : "Written off";
        return ResponseEntity.ok(investmentService.writeOffInvestment(id, principal.getTenantId(), notes));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<InvestmentValueHistoryDto>> getValueHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.getValueHistory(id, principal.getTenantId()));
    }

    @PostMapping("/{id}/topup")
    public ResponseEntity<InvestmentDto> topupInvestment(
            @PathVariable Long id,
            @Valid @RequestBody TopupInvestmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.topupInvestment(id, principal.getTenantId(), principal.getMemberId(), request));
    }

    @GetMapping("/{id}/topups")
    public ResponseEntity<List<InvestmentTopupDto>> getTopupHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(investmentService.getTopupHistory(id, principal.getTenantId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvestment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        investmentService.deleteInvestment(id, principal.getTenantId());
        return ResponseEntity.noContent().build();
    }
}
