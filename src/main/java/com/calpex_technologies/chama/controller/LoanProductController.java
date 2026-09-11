package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.LoanProductDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.LoanProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loan-products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService loanProductService;

    @GetMapping
    public ResponseEntity<List<LoanProductDto>> getAllLoanProducts(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanProductService.getAllLoanProducts(principal.getTenantId()));
    }

    @GetMapping("/active")
    public ResponseEntity<List<LoanProductDto>> getActiveLoanProducts(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanProductService.getActiveLoanProducts(principal.getTenantId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanProductDto> getLoanProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanProductService.getLoanProduct(principal.getTenantId(), id));
    }

    @PostMapping
    public ResponseEntity<LoanProductDto> createLoanProduct(
            @RequestBody LoanProductDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanProductService.createLoanProduct(principal.getTenantId(), dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LoanProductDto> updateLoanProduct(
            @PathVariable Long id,
            @RequestBody LoanProductDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(loanProductService.updateLoanProduct(principal.getTenantId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLoanProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        loanProductService.deleteLoanProduct(principal.getTenantId(), id);
        return ResponseEntity.ok().build();
    }
}
