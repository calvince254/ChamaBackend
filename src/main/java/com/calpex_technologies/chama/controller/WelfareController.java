package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.WelfareContributionDto;
import com.calpex_technologies.chama.dto.WelfareEventDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
}
