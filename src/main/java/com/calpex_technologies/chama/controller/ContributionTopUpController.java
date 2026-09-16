package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.ContributionTopUpDto;
import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.ContributionTopUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contribution-topups")
@RequiredArgsConstructor
public class ContributionTopUpController {

    private final ContributionTopUpService topUpService;

    /**
     * Get all top-up requests with pagination and filters (admin view)
     */
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_CONTRIBUTION_TOPUPS')")
    public ResponseEntity<PageResponse<ContributionTopUpDto>> getTopUps(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long contributionTypeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(topUpService.getTopUps(principal.getTenantId(), status, memberId, contributionTypeId, page, size));
    }

    /**
     * Get top-ups for current member (my top-ups)
     */
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('REQUEST_CONTRIBUTION_TOPUP')")
    public ResponseEntity<PageResponse<ContributionTopUpDto>> getMyTopUps(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(topUpService.getMyTopUps(principal.getTenantId(), principal.getMemberId(), page, size));
    }

    /**
     * Get a single top-up request
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_CONTRIBUTION_TOPUPS')")
    public ResponseEntity<ContributionTopUpDto> getTopUp(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(topUpService.getTopUp(id, principal.getTenantId()));
    }

    /**
     * Create a new top-up request (member initiates)
     */
    @PostMapping
    @PreAuthorize("hasAuthority('REQUEST_CONTRIBUTION_TOPUP')")
    public ResponseEntity<ContributionTopUpDto> createTopUp(
            @RequestBody ContributionTopUpDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Set the member ID to current user if not provided
        if (dto.getMemberId() == null) {
            dto.setMemberId(principal.getMemberId());
        }
        return ResponseEntity.ok(topUpService.createTopUp(dto, principal.getTenantId()));
    }

    /**
     * Approve a top-up request (admin action)
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('APPROVE_CONTRIBUTION_TOPUP')")
    public ResponseEntity<ContributionTopUpDto> approveTopUp(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(topUpService.approveTopUp(id, principal.getMemberId(), principal.getTenantId()));
    }

    /**
     * Reject a top-up request (admin action)
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('APPROVE_CONTRIBUTION_TOPUP')")
    public ResponseEntity<ContributionTopUpDto> rejectTopUp(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(topUpService.rejectTopUp(id, reason, principal.getMemberId(), principal.getTenantId()));
    }

    /**
     * Get count of pending top-ups (for badge display)
     */
    @GetMapping("/pending-count")
    @PreAuthorize("hasAuthority('VIEW_CONTRIBUTION_TOPUPS')")
    public ResponseEntity<Map<String, Long>> getPendingCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        long count = topUpService.getPendingCount(principal.getTenantId());
        return ResponseEntity.ok(Map.of("count", count));
    }
}
