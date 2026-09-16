package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.entity.Invoice;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.repository.InvoiceRepository;
import com.calpex_technologies.chama.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for security-related checks used in @PreAuthorize expressions.
 */
@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final MemberRepository memberRepository;
    private final InvoiceRepository invoiceRepository;

    /**
     * Check if the current user is a member of the given tenant
     */
    public boolean isTenantMember(Long tenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return false;
        }

        String email = auth.getName();
        Optional<Member> member = memberRepository.findByEmail(email);
        
        return member.map(m -> m.getTenant().getId().equals(tenantId)).orElse(false);
    }

    /**
     * Check if the current user can access a specific invoice
     */
    public boolean canAccessInvoice(Long invoiceId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return false;
        }

        // Super admins can access any invoice (handled by @PreAuthorize)
        // Check if user is a member of the invoice's tenant
        String email = auth.getName();
        Optional<Member> member = memberRepository.findByEmail(email);
        
        if (member.isEmpty()) {
            return false;
        }

        Optional<Invoice> invoice = invoiceRepository.findById(invoiceId);
        return invoice.map(inv -> inv.getTenant().getId().equals(member.get().getTenant().getId())).orElse(false);
    }

    /**
     * Get the current user's tenant ID
     */
    public Long getCurrentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }

        String email = auth.getName();
        Optional<Member> member = memberRepository.findByEmail(email);
        
        return member.map(m -> m.getTenant().getId()).orElse(null);
    }

    /**
     * Check if the current user is a super admin
     */
    public boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    /**
     * Get the current user's member ID
     */
    public Long getCurrentMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }

        String email = auth.getName();
        Optional<Member> member = memberRepository.findByEmail(email);
        
        return member.map(Member::getId).orElse(null);
    }
}
