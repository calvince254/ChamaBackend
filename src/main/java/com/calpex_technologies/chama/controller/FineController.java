package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.FineDto;
import com.calpex_technologies.chama.dto.ImposeFineRequest;
import com.calpex_technologies.chama.dto.RecordPaymentRequest;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.FineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings/{meetingId}/fines")
@RequiredArgsConstructor
public class FineController {

    private final FineService fineService;

    @GetMapping
    public ResponseEntity<List<FineDto>> getMeetingFines(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(fineService.getMeetingFines(principal.getTenantId(), meetingId));
    }

    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<FineDto>> getMemberMeetingFines(
            @PathVariable Long meetingId,
            @PathVariable Long memberId) {
        return ResponseEntity.ok(fineService.getMemberMeetingFines(meetingId, memberId));
    }

    @PostMapping
    public ResponseEntity<FineDto> imposeFine(
            @PathVariable Long meetingId,
            @RequestBody ImposeFineRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(fineService.imposeFine(principal.getTenantId(), meetingId, request, principal.getMemberId()));
    }

    /**
     * Record a payment for a fine (supports partial payments)
     */
    @PostMapping("/{fineId}/pay")
    public ResponseEntity<FineDto> recordFinePayment(
            @PathVariable Long meetingId,
            @PathVariable Long fineId,
            @RequestBody(required = false) RecordPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        // If no amount specified, pay full balance
        if (request == null || request.getAmount() == null) {
            return ResponseEntity.ok(fineService.markFinePaid(principal.getTenantId(), fineId));
        }
        return ResponseEntity.ok(fineService.recordPayment(principal.getTenantId(), fineId, request.getAmount()));
    }

    @PostMapping("/{fineId}/unpay")
    public ResponseEntity<FineDto> markFineUnpaid(
            @PathVariable Long meetingId,
            @PathVariable Long fineId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(fineService.markFineUnpaid(principal.getTenantId(), fineId));
    }

    @PostMapping("/{fineId}/waive")
    public ResponseEntity<FineDto> waiveFine(
            @PathVariable Long meetingId,
            @PathVariable Long fineId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(fineService.waiveFine(principal.getTenantId(), fineId));
    }

    @DeleteMapping("/{fineId}")
    public ResponseEntity<Void> deleteFine(
            @PathVariable Long meetingId,
            @PathVariable Long fineId,
            @AuthenticationPrincipal UserPrincipal principal) {
        fineService.deleteFine(principal.getTenantId(), fineId);
        return ResponseEntity.ok().build();
    }
}
