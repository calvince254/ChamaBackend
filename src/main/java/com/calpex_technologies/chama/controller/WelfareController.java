package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.WelfareContributionDto;
import com.calpex_technologies.chama.dto.WelfareEventDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/welfare")
@RequiredArgsConstructor
public class WelfareController {

    private final WelfareService welfareService;

    @GetMapping
    public ResponseEntity<List<WelfareEventDto>> getAllWelfareEvents(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(welfareService.getAllWelfareEvents(principal.getTenantId()));
    }

    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<WelfareEventDto>> getWelfareEventsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<WelfareEventDto> result = welfareService.getWelfareEventsPaginated(
                principal.getTenantId(), page, size, status);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WelfareEventDto> getWelfareEvent(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(welfareService.getWelfareEvent(id, principal.getTenantId()));
    }

    @PostMapping
    public ResponseEntity<WelfareEventDto> createWelfareEvent(@RequestBody WelfareEventDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(welfareService.createWelfareEvent(dto, principal.getTenantId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WelfareEventDto> updateWelfareEvent(@PathVariable Long id, @RequestBody WelfareEventDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(welfareService.updateWelfareEvent(id, dto, principal.getTenantId()));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<WelfareEventDto> closeEvent(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(welfareService.closeEvent(id, principal.getTenantId()));
    }

    // ==================== CONTRIBUTION MANAGEMENT ====================

    @GetMapping("/{id}/contributions")
    public ResponseEntity<List<WelfareContributionDto>> getEventContributions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(welfareService.getEventContributions(id, principal.getTenantId()));
    }

    @GetMapping("/{id}/contributions/paginated")
    public ResponseEntity<PageResponse<WelfareContributionDto>> getEventContributionsPaginated(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<WelfareContributionDto> result = welfareService.getEventContributionsPaginated(
                id, principal.getTenantId(), page, size);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @PutMapping("/{eventId}/contributions/{memberId}")
    public ResponseEntity<WelfareContributionDto> updateContributionPayment(
            @PathVariable Long eventId,
            @PathVariable Long memberId,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        Boolean paid = body.get("paid");
        return ResponseEntity.ok(welfareService.updateContributionPayment(eventId, memberId, paid, principal.getTenantId()));
    }

    @PostMapping("/{id}/contributions/mark-all-paid")
    public ResponseEntity<Void> markAllAsPaid(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        welfareService.markAllAsPaid(id, principal.getTenantId());
        return ResponseEntity.ok().build();
    }

    // ==================== PAY FROM CONTRIBUTION FUND ====================

    /**
     * Pay welfare contribution using funds from a contribution pool
     */
    @PostMapping("/{eventId}/contributions/{memberId}/pay-from-fund")
    public ResponseEntity<WelfareContributionDto> payFromContributionFund(
            @PathVariable Long eventId,
            @PathVariable Long memberId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long contributionTypeId = body.get("contributionTypeId") != null 
                ? ((Number) body.get("contributionTypeId")).longValue() : null;
        if (contributionTypeId == null) {
            throw new RuntimeException("contributionTypeId is required");
        }
        
        // Optional amount parameter for partial payments
        BigDecimal amount = null;
        if (body.get("amount") != null) {
            amount = new BigDecimal(body.get("amount").toString());
        }
        
        return ResponseEntity.ok(welfareService.payFromContributionFund(
                eventId, memberId, contributionTypeId, amount, principal.getTenantId()));
    }

    /**
     * Get member's contribution balances (available pools to pay from)
     */
    @GetMapping("/member/{memberId}/contribution-balances")
    public ResponseEntity<List<WelfareService.ContributionBalanceDto>> getMemberContributionBalances(
            @PathVariable Long memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(welfareService.getMemberContributionBalances(memberId, principal.getTenantId()));
    }
    
    /**
     * Debug endpoint to check welfare contributions for a member
     */
    @GetMapping("/debug/member/{memberId}/welfare-contributions")
    public ResponseEntity<List<Map<String, Object>>> debugMemberWelfareContributions(
            @PathVariable Long memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(welfareService.debugGetMemberWelfareContributions(memberId, principal.getTenantId()));
    }
}
