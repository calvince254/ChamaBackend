package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.PenaltyDto;
import com.calpex_technologies.chama.dto.RecordPaymentRequest;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.PenaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/penalties")
@RequiredArgsConstructor
public class PenaltyController {

    private final PenaltyService penaltyService;

    @GetMapping
    public ResponseEntity<List<PenaltyDto>> getAllPenalties(
            @RequestParam(required = false) String month,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (month != null) {
            YearMonth ym = YearMonth.parse(month);
            return ResponseEntity.ok(penaltyService.getPenaltiesByMonth(principal.getTenantId(), ym));
        }
        return ResponseEntity.ok(penaltyService.getAllPenalties(principal.getTenantId()));
    }
    
    /**
     * Get penalties with pagination.
     * Default: 10 items per page.
     */
    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<PenaltyDto>> getPenaltiesPaginated(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String penaltyType,
            @RequestParam(required = false) Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<PenaltyDto> response = penaltyService.getPenaltiesPaginated(
                principal.getTenantId(), status, penaltyType, memberId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unpaid")
    public ResponseEntity<List<PenaltyDto>> getUnpaidPenalties(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(penaltyService.getUnpaidPenalties(principal.getTenantId()));
    }

    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<PenaltyDto>> getMemberPenalties(
            @PathVariable Long memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(penaltyService.getMemberPenalties(principal.getTenantId(), memberId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PenaltyDto> getPenalty(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(penaltyService.getPenalty(principal.getTenantId(), id));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, BigDecimal>> getPenaltySummary(
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, BigDecimal> summary = new HashMap<>();
        summary.put("total", penaltyService.getTotalPenalties(principal.getTenantId()));
        summary.put("paid", penaltyService.getTotalPaidPenalties(principal.getTenantId()));
        summary.put("unpaid", penaltyService.getTotalUnpaidPenalties(principal.getTenantId()));
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<PenaltyDto> recordPayment(
            @PathVariable Long id,
            @RequestBody RecordPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(penaltyService.recordPayment(principal.getTenantId(), id, request.getAmount()));
    }

    @PostMapping("/{id}/waive")
    public ResponseEntity<PenaltyDto> waivePenalty(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(penaltyService.waivePenalty(principal.getTenantId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePenalty(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        penaltyService.deletePenalty(principal.getTenantId(), id);
        return ResponseEntity.ok().build();
    }
}
