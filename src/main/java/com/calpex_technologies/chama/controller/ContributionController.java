package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.ContributionDto;
import com.calpex_technologies.chama.dto.GenerateContributionsRequest;
import com.calpex_technologies.chama.dto.RecordPaymentRequest;
import com.calpex_technologies.chama.scheduler.ContributionScheduler;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.ContributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contributions")
@RequiredArgsConstructor
public class ContributionController {

    private final ContributionService contributionService;
    private final ContributionScheduler contributionScheduler;

    @GetMapping
    public ResponseEntity<List<ContributionDto>> getAllContributions(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long contributionTypeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        if (month != null && contributionTypeId != null) {
            YearMonth ym = YearMonth.parse(month);
            // Return only saved contributions (not virtual pending records)
            return ResponseEntity.ok(contributionService.getSavedContributionsByTypeAndMonth(
                    principal.getTenantId(), contributionTypeId, ym));
        } else if (month != null) {
            YearMonth ym = YearMonth.parse(month);
            return ResponseEntity.ok(contributionService.getContributionsByMonth(principal.getTenantId(), ym));
        }
        return ResponseEntity.ok(contributionService.getAllContributions(principal.getTenantId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContributionDto> getContribution(
            @PathVariable Long id, 
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionService.getContribution(id, principal.getTenantId()));
    }

    @PostMapping
    public ResponseEntity<ContributionDto> createContribution(
            @RequestBody ContributionDto dto, 
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionService.createContribution(dto, principal.getTenantId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContributionDto> updateContribution(
            @PathVariable Long id, 
            @RequestBody ContributionDto dto, 
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionService.updateContribution(id, dto, principal.getTenantId()));
    }

    /**
     * Record a payment for a contribution (supports partial payments)
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<ContributionDto> recordPayment(
            @PathVariable Long id,
            @RequestBody RecordPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionService.recordPayment(id, request.getAmount(), principal.getTenantId()));
    }

    /**
     * Generate contribution records for selected members (or all if memberIds empty) for a specific contribution type and month
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateContributions(
            @RequestBody GenerateContributionsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        YearMonth ym = YearMonth.parse(request.getMonth());
        List<ContributionDto> created = contributionService.generateContributionsForMembers(
                principal.getTenantId(), request.getContributionTypeId(), ym, request.getMemberIds());
        
        Map<String, Object> response = new HashMap<>();
        response.put("created", created.size());
        response.put("contributions", created);
        response.put("message", created.size() + " contribution records generated");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get members available for generating contributions (active members without contribution for this type/month)
     */
    @GetMapping("/available-members")
    public ResponseEntity<List<Map<String, Object>>> getAvailableMembers(
            @RequestParam Long contributionTypeId,
            @RequestParam String month,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        YearMonth ym = YearMonth.parse(month);
        return ResponseEntity.ok(contributionService.getMembersWithoutContribution(
                principal.getTenantId(), contributionTypeId, ym));
    }

    /**
     * Generate contribution records for ALL active contribution types for a month
     */
    @PostMapping("/generate-all")
    public ResponseEntity<Map<String, Object>> generateAllContributions(
            @RequestParam String month,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        YearMonth ym = YearMonth.parse(month);
        int created = contributionService.generateAllMonthlyContributions(principal.getTenantId(), ym);
        
        Map<String, Object> response = new HashMap<>();
        response.put("created", created);
        response.put("message", created + " contribution records generated for all active types");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Check and impose penalties for overdue contributions for a specific month
     */
    @PostMapping("/check-penalties")
    public ResponseEntity<Map<String, Object>> checkAndImposePenalties(
            @RequestParam String month,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        YearMonth ym = YearMonth.parse(month);
        int penaltiesImposed = contributionScheduler.checkAndImposePenaltiesForMonth(principal.getTenantId(), ym);
        
        Map<String, Object> response = new HashMap<>();
        response.put("penaltiesImposed", penaltiesImposed);
        response.put("message", penaltiesImposed + " late payment penalties imposed");
        
        return ResponseEntity.ok(response);
    }
}
