package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.CreateMemberRequest;
import com.calpex_technologies.chama.dto.MemberDto;
import com.calpex_technologies.chama.dto.MemberFinancialSummaryDto;
import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.TransactionDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@Slf4j
public class MemberController {

    private final MemberService memberService;

    /**
     * Get all members (legacy endpoint, returns all without pagination).
     * Kept for backward compatibility.
     */
    @GetMapping
    public ResponseEntity<List<MemberDto>> getAllMembers(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/members - principal: memberId={}, tenantId={}, email={}, superAdmin={}", 
                principal.getMemberId(), principal.getTenantId(), principal.getEmail(), principal.isSuperAdmin());
        List<MemberDto> members = memberService.getAllMembers(principal.getTenantId());
        log.info("Returning {} members for tenantId={}", members.size(), principal.getTenantId());
        return ResponseEntity.ok(members);
    }
    
    /**
     * Get members with pagination, search, and status filter.
     * Default: 10 items per page.
     */
    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<MemberDto>> getMembersPaginated(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        log.info("GET /api/members/paginated - page={}, size={}, search={}, status={}", page, size, search, status);
        PageResponse<MemberDto> response = memberService.getMembersPaginated(
                principal.getTenantId(), page, size, search, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MemberDto> getMember(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(memberService.getMember(id, principal.getTenantId()));
    }

    @PostMapping
    public ResponseEntity<MemberDto> createMember(@Valid @RequestBody CreateMemberRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(memberService.createMember(request, principal.getTenantId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MemberDto> updateMember(@PathVariable Long id, @RequestBody MemberDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(memberService.updateMember(id, dto, principal.getTenantId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        memberService.deleteMember(id, principal.getTenantId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Hard delete a member and ALL related data.
     * This permanently removes the member and all their contributions, loans, fines, penalties, transactions, etc.
     * WARNING: This action cannot be undone!
     */
    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDeleteMember(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        memberService.hardDeleteMember(id, principal.getTenantId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/financial-summary")
    public ResponseEntity<MemberFinancialSummaryDto> getMemberFinancialSummary(
            @PathVariable Long id, 
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(memberService.getMemberFinancialSummary(id, principal.getTenantId()));
    }
    
    /**
     * Get paginated transactions for a specific member.
     * Default: 10 items per page.
     */
    @GetMapping("/{id}/transactions/paginated")
    public ResponseEntity<PageResponse<TransactionDto>> getMemberTransactionsPaginated(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<TransactionDto> response = memberService.getMemberTransactionsPaginated(
                id, principal.getTenantId(), type, page, size);
        return ResponseEntity.ok(response);
    }
}
