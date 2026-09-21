package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.PayableDto;
import com.calpex_technologies.chama.entity.PayableType;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.PayableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for Payables - universal payment abstractions.
 * Members can view their payables, admins can view all payables.
 */
@RestController
@RequestMapping("/api/payables")
@RequiredArgsConstructor
@Slf4j
public class PayableController {

    private final PayableService payableService;

    /**
     * Get a specific payable by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PayableDto> getPayable(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        PayableDto payable = payableService.getPayable(id, principal.getTenantId());
        
        // Security check: members can only view their own payables
        if (!principal.isSuperAdmin() && 
            !payable.getMemberId().equals(principal.getMemberId())) {
            return ResponseEntity.status(403).build();
        }
        
        return ResponseEntity.ok(payable);
    }

    /**
     * Get all outstanding payables for the current member.
     * This is what appears in the member's "Dues" view.
     */
    @GetMapping("/my")
    public ResponseEntity<List<PayableDto>> getMyPayables(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<PayableDto> payables = payableService.getOutstandingPayablesForMember(principal.getMemberId());
        return ResponseEntity.ok(payables);
    }

    /**
     * Get all payables for the current member (including paid ones).
     */
    @GetMapping("/my/all")
    public ResponseEntity<List<PayableDto>> getAllMyPayables(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<PayableDto> payables = payableService.getPayablesForMember(
                principal.getMemberId(), principal.getTenantId());
        return ResponseEntity.ok(payables);
    }

    /**
     * Get all payables for the tenant (admin view).
     */
    @GetMapping
    public ResponseEntity<PageResponse<PayableDto>> getAllPayables(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PayableDto> payables = payableService.getAllPayables(principal.getTenantId(), pageable);
        return ResponseEntity.ok(PageResponse.from(payables));
    }

    /**
     * Get payables by type (admin view).
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<PageResponse<PayableDto>> getPayablesByType(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            PayableType payableType = PayableType.valueOf(type.toUpperCase());
            Pageable pageable = PageRequest.of(page, size);
            Page<PayableDto> payables = payableService.getPayablesByType(
                    principal.getTenantId(), payableType, pageable);
            return ResponseEntity.ok(PageResponse.from(payables));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get payables awaiting confirmation (treasurer view).
     */
    @GetMapping("/awaiting-confirmation")
    public ResponseEntity<List<PayableDto>> getAwaitingConfirmation(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<PayableDto> payables = payableService.getAwaitingConfirmation(principal.getTenantId());
        return ResponseEntity.ok(payables);
    }

    /**
     * Get count of outstanding payables for the current member.
     */
    @GetMapping("/my/count")
    public ResponseEntity<Long> getOutstandingCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        long count = payableService.countOutstandingForMember(principal.getMemberId());
        return ResponseEntity.ok(count);
    }
}
