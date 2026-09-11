package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.FineTypeDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.FineTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fine-types")
@RequiredArgsConstructor
public class FineTypeController {

    private final FineTypeService fineTypeService;

    @GetMapping
    public ResponseEntity<List<FineTypeDto>> getAllFineTypes(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(fineTypeService.getAllFineTypes(principal.getTenantId()));
    }

    @GetMapping("/active")
    public ResponseEntity<List<FineTypeDto>> getActiveFineTypes(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(fineTypeService.getActiveFineTypes(principal.getTenantId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FineTypeDto> getFineType(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(fineTypeService.getFineType(principal.getTenantId(), id));
    }

    @PostMapping
    public ResponseEntity<FineTypeDto> createFineType(
            @RequestBody FineTypeDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(fineTypeService.createFineType(principal.getTenantId(), dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FineTypeDto> updateFineType(
            @PathVariable Long id,
            @RequestBody FineTypeDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(fineTypeService.updateFineType(principal.getTenantId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFineType(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        fineTypeService.deleteFineType(principal.getTenantId(), id);
        return ResponseEntity.ok().build();
    }
}
