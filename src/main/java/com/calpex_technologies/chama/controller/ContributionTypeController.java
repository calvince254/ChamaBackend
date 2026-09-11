package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.ContributionTypeDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.ContributionTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contribution-types")
@RequiredArgsConstructor
public class ContributionTypeController {

    private final ContributionTypeService contributionTypeService;

    @GetMapping
    public ResponseEntity<List<ContributionTypeDto>> getAllContributionTypes(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionTypeService.getAllContributionTypes(principal.getTenantId()));
    }

    @GetMapping("/active")
    public ResponseEntity<List<ContributionTypeDto>> getActiveContributionTypes(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionTypeService.getActiveContributionTypes(principal.getTenantId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContributionTypeDto> getContributionType(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionTypeService.getContributionType(principal.getTenantId(), id));
    }

    @PostMapping
    public ResponseEntity<ContributionTypeDto> createContributionType(
            @RequestBody ContributionTypeDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionTypeService.createContributionType(principal.getTenantId(), dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContributionTypeDto> updateContributionType(
            @PathVariable Long id,
            @RequestBody ContributionTypeDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contributionTypeService.updateContributionType(principal.getTenantId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContributionType(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        contributionTypeService.deleteContributionType(principal.getTenantId(), id);
        return ResponseEntity.ok().build();
    }
}
