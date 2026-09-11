package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.ShareCalculationDto;
import com.calpex_technologies.chama.dto.ShareDto;
import com.calpex_technologies.chama.dto.ShareSettingsDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.ShareCalculationService;
import com.calpex_technologies.chama.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final ShareCalculationService shareCalculationService;

    // ==================== LEGACY SHARE PURCHASE (OLD SYSTEM) ====================
    
    @GetMapping("/legacy")
    public ResponseEntity<List<ShareDto>> getAllShares(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(shareService.getAllShares(principal.getTenantId()));
    }

    @GetMapping("/legacy/{id}")
    public ResponseEntity<ShareDto> getShare(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(shareService.getShare(id, principal.getTenantId()));
    }

    @PostMapping("/purchase")
    public ResponseEntity<ShareDto> purchaseShares(
            @RequestParam Long memberId,
            @RequestParam int quantity,
            @RequestParam BigDecimal shareValue,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(shareService.purchaseShares(memberId, quantity, shareValue, principal.getTenantId()));
    }

    // ==================== NEW SHARE CALCULATION SYSTEM ====================

    @GetMapping("/settings")
    public ResponseEntity<ShareSettingsDto> getShareSettings(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(shareCalculationService.getShareSettings(principal.getTenantId()));
    }

    @PutMapping("/settings")
    public ResponseEntity<ShareSettingsDto> saveShareSettings(
            @RequestBody ShareSettingsDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(shareCalculationService.saveShareSettings(dto, principal.getTenantId()));
    }

    @GetMapping
    public ResponseEntity<ShareCalculationDto> calculateShares(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(shareCalculationService.calculateShares(principal.getTenantId()));
    }
}
