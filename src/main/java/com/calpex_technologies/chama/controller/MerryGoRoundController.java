package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.MerryGoRoundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merry-go-round")
@RequiredArgsConstructor
public class MerryGoRoundController {

    private final MerryGoRoundService merryGoRoundService;

    // ==================== CYCLES ====================

    @GetMapping("/cycles")
    public ResponseEntity<List<MerryGoRoundCycleDto>> getAllCycles(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getAllCycles(principal.getTenantId()));
    }

    @GetMapping("/cycles/{id}")
    public ResponseEntity<MerryGoRoundCycleDto> getCycle(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getCycle(id, principal.getTenantId()));
    }

    @PostMapping("/cycles")
    public ResponseEntity<MerryGoRoundCycleDto> createCycle(
            @Valid @RequestBody CreateMerryGoRoundCycleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.createCycle(request, principal.getTenantId()));
    }

    @PutMapping("/cycles/{id}")
    public ResponseEntity<MerryGoRoundCycleDto> updateCycle(
            @PathVariable Long id,
            @RequestBody MerryGoRoundCycleDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.updateCycle(id, dto, principal.getTenantId()));
    }

    @DeleteMapping("/cycles/{id}")
    public ResponseEntity<Void> deleteCycle(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        merryGoRoundService.deleteCycle(id, principal.getTenantId());
        return ResponseEntity.noContent().build();
    }

    // ==================== PARTICIPANTS ====================

    @GetMapping("/cycles/{cycleId}/participants")
    public ResponseEntity<List<MerryGoRoundParticipantDto>> getParticipants(
            @PathVariable Long cycleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getParticipants(cycleId, principal.getTenantId()));
    }

    @PostMapping("/cycles/{cycleId}/participants/{memberId}")
    public ResponseEntity<MerryGoRoundParticipantDto> addParticipant(
            @PathVariable Long cycleId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.addParticipant(cycleId, memberId, principal.getTenantId()));
    }

    @DeleteMapping("/cycles/{cycleId}/participants/{memberId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable Long cycleId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        merryGoRoundService.removeParticipant(cycleId, memberId, principal.getTenantId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cycles/{cycleId}/participants/reorder")
    public ResponseEntity<Void> reorderParticipants(
            @PathVariable Long cycleId,
            @RequestBody List<Long> memberIds,
            @AuthenticationPrincipal UserPrincipal principal) {
        merryGoRoundService.reorderParticipants(cycleId, memberIds, principal.getTenantId());
        return ResponseEntity.ok().build();
    }

    // ==================== ROUNDS ====================

    @GetMapping
    public ResponseEntity<List<MerryGoRoundRoundDto>> getAllRounds(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getAllRounds(principal.getTenantId()));
    }

    @GetMapping("/cycles/{cycleId}/rounds")
    public ResponseEntity<List<MerryGoRoundRoundDto>> getRoundsByCycle(
            @PathVariable Long cycleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getRoundsByCycle(cycleId, principal.getTenantId()));
    }

    @GetMapping("/rounds/{id}")
    public ResponseEntity<MerryGoRoundRoundDto> getRound(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getRoundWithContributions(id, principal.getTenantId()));
    }

    @PostMapping("/rounds/{id}/pay")
    public ResponseEntity<MerryGoRoundRoundDto> markRoundAsPaid(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.markRoundAsPaid(id, principal.getTenantId()));
    }

    @PostMapping("/rounds/{id}/payout")
    public ResponseEntity<MerryGoRoundRoundDto> makeRoundPayout(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        java.math.BigDecimal amount = null;
        if (body != null && body.get("amount") != null) {
            amount = new java.math.BigDecimal(body.get("amount").toString());
        }
        return ResponseEntity.ok(merryGoRoundService.makeRoundPayout(id, amount, principal.getTenantId()));
    }

    // ==================== CONTRIBUTIONS ====================

    @GetMapping("/rounds/{roundId}/contributions")
    public ResponseEntity<List<MerryGoRoundContributionDto>> getContributionsByRound(
            @PathVariable Long roundId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getContributionsByRound(roundId, principal.getTenantId()));
    }

    @GetMapping("/cycles/{cycleId}/contributions")
    public ResponseEntity<List<MerryGoRoundContributionDto>> getContributionsByCycle(
            @PathVariable Long cycleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getContributionsByCycle(cycleId, principal.getTenantId()));
    }

    @PostMapping("/contributions/{id}/pay")
    public ResponseEntity<MerryGoRoundContributionDto> markContributionAsPaid(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String paymentRef = body != null ? body.get("paymentReference") : null;
        return ResponseEntity.ok(merryGoRoundService.markContributionAsPaid(id, paymentRef, principal.getTenantId()));
    }

    @PostMapping("/contributions/pay-multiple")
    public ResponseEntity<List<MerryGoRoundContributionDto>> markMultipleContributionsAsPaid(
            @RequestBody List<Long> contributionIds,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.markMultipleContributionsAsPaid(contributionIds, principal.getTenantId()));
    }

    // ==================== FINES ====================

    @GetMapping("/cycles/{cycleId}/fines")
    public ResponseEntity<List<MerryGoRoundFineDto>> getFinesByCycle(
            @PathVariable Long cycleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getFinesByCycle(cycleId, principal.getTenantId()));
    }

    @GetMapping("/cycles/{cycleId}/my-fines")
    public ResponseEntity<List<MerryGoRoundFineDto>> getMyFinesByCycle(
            @PathVariable Long cycleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getMyFinesByCycle(cycleId, principal.getMemberId(), principal.getTenantId()));
    }

    @PostMapping("/fines")
    public ResponseEntity<List<MerryGoRoundFineDto>> imposeFine(
            @Valid @RequestBody ImposeMgrFineRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.imposeFine(request, principal.getTenantId()));
    }

    @PostMapping("/contributions/{contributionId}/fine")
    public ResponseEntity<MerryGoRoundFineDto> imposeFineForContribution(
            @PathVariable Long contributionId,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String reason = body != null ? (String) body.get("reason") : null;
        java.math.BigDecimal amount = null;
        if (body != null && body.get("amount") != null) {
            amount = new java.math.BigDecimal(body.get("amount").toString());
        }
        return ResponseEntity.ok(merryGoRoundService.imposeFineForContribution(
                contributionId, reason, amount, principal.getTenantId()));
    }

    @PostMapping("/fines/{id}/pay")
    public ResponseEntity<MerryGoRoundFineDto> payFine(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.payFine(id, principal.getTenantId()));
    }

    /**
     * Initiate payment for an MGR fine using the unified payment system.
     * Supports MANUAL (reference code), STK_PUSH (M-PESA), and IN_APP_SMS payment methods.
     */
    @PostMapping("/fines/{id}/initiate-payment")
    public ResponseEntity<InitiatePaymentResponse> initiateFinepayment(
            @PathVariable Long id,
            @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.initiateFinePayment(id, request, principal.getMemberId(), principal.getTenantId()));
    }

    /**
     * Admin records a payment for an MGR fine (bypasses member payment flow).
     */
    @PostMapping("/fines/{id}/record-payment")
    public ResponseEntity<MerryGoRoundFineDto> recordFinePayment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        java.math.BigDecimal amount = null;
        if (body != null && body.get("amount") != null) {
            amount = new java.math.BigDecimal(body.get("amount").toString());
        }
        return ResponseEntity.ok(merryGoRoundService.recordFinePayment(id, amount, principal.getTenantId()));
    }

    @PostMapping("/fines/{id}/waive")
    public ResponseEntity<MerryGoRoundFineDto> waiveFine(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String reason = body.get("reason");
        return ResponseEntity.ok(merryGoRoundService.waiveFine(id, reason, principal.getName(), principal.getTenantId()));
    }

    // ==================== SUMMARY / REPORTS ====================

    @GetMapping("/summary")
    public ResponseEntity<MerryGoRoundSummaryDto> getSummary(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(merryGoRoundService.getSummary(principal.getTenantId()));
    }
}
