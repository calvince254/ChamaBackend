package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.PasswordHistory;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.PasswordHistoryRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import com.calpex_technologies.chama.security.JwtUtil;
import com.calpex_technologies.chama.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getEmail().trim();
        List<Member> members;
        boolean isPhoneLogin = PhoneUtils.isPhoneNumber(identifier);
        
        // Determine if login is by email or phone
        if (isPhoneLogin) {
            // Login by phone - search all possible phone format variations
            List<String> phoneVariants = PhoneUtils.getPhoneVariants(identifier);
            members = memberRepository.findAllByPhoneIn(phoneVariants);
        } else {
            // Login by email
            members = memberRepository.findAllByEmail(identifier);
        }
        
        if (members.isEmpty()) {
            throw new RuntimeException("Invalid credentials");
        }
        
        // Find member(s) with matching password
        List<Member> matchingMembers = members.stream()
                .filter(m -> passwordEncoder.matches(request.getPassword(), m.getPassword()))
                .filter(m -> m.getStatus() == Member.MemberStatus.ACTIVE)
                .collect(Collectors.toList());
        
        if (matchingMembers.isEmpty()) {
            throw new RuntimeException("Invalid credentials");
        }

        Member validMember = matchingMembers.get(0);

        // Handle first login - force password change
        if (Boolean.TRUE.equals(validMember.getFirstLogin())) {
            validMember.setMustChangePassword(true);
            validMember.setFirstLogin(false);
            memberRepository.save(validMember);
        }

        // Check if super admin (no tenant)
        if (validMember.isSuperAdmin()) {
            return buildSuperAdminResponse(validMember);
        }

        // Multi-tenant check: Only show selection if the SAME PERSON (same email + same phone + same password)
        // exists in multiple chamas. Different people with different emails are separate accounts.
        String validEmail = validMember.getEmail();
        String validPhone = validMember.getPhone();
        
        // Filter to only members with same email AND same phone (i.e., same person in multiple chamas)
        List<Member> samePersonMemberships = matchingMembers.stream()
                .filter(m -> m.getTenant() != null)
                .filter(m -> validEmail != null && validEmail.equals(m.getEmail()))
                .filter(m -> validPhone != null && validPhone.equals(m.getPhone()))
                .collect(Collectors.toList());
        
        // If same person is in multiple chamas, return selection list
        if (samePersonMemberships.size() > 1) {
            return buildMultiTenantResponse(validMember, samePersonMemberships);
        }

        // Single tenant - regular login
        Tenant tenant = validMember.getTenant();
        if (tenant == null) {
            throw new RuntimeException("No tenant associated with this account");
        }

        return buildAuthResponse(validMember, tenant);
    }

    /**
     * Login to a specific tenant (used after selecting from multi-tenant list)
     */
    @Transactional(readOnly = true)
    public AuthResponse loginToTenant(LoginRequest request, Long tenantId) {
        String identifier = request.getEmail().trim();
        Member member;
        
        // Determine if login is by email or phone
        if (PhoneUtils.isPhoneNumber(identifier)) {
            // Login by phone - try all format variations
            List<String> phoneVariants = PhoneUtils.getPhoneVariants(identifier);
            member = phoneVariants.stream()
                    .map(phone -> memberRepository.findByPhoneAndTenantId(phone, tenantId))
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        } else {
            // Login by email
            member = memberRepository.findByEmailAndTenantId(identifier, tenantId)
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        }

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (member.getStatus() != Member.MemberStatus.ACTIVE) {
            throw new RuntimeException("Account is not active");
        }

        Tenant tenant = member.getTenant();
        if (tenant == null) {
            throw new RuntimeException("Tenant not found");
        }

        return buildAuthResponse(member, tenant);
    }

    // Keep for backward compatibility but routes to same login
    @Transactional(readOnly = true)
    public AuthResponse superAdminLogin(SuperAdminLoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!member.isSuperAdmin()) {
            throw new RuntimeException("Not authorized");
        }

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        return buildSuperAdminResponse(member);
    }

    /**
     * Change password for the currently authenticated user.
     * Also clears the mustChangePassword flag after successful change.
     * Validates that new password is not one of the last 5 passwords used.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        // Get current user from security context
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Not authenticated");
        }
        
        // Get member ID from UserPrincipal (set by JwtAuthenticationFilter)
        Object principal = authentication.getPrincipal();
        Long memberId;
        if (principal instanceof com.calpex_technologies.chama.security.UserPrincipal userPrincipal) {
            memberId = userPrincipal.getMemberId();
        } else {
            // Fallback to email lookup
            String email = authentication.getName();
            Member m = memberRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            memberId = m.getId();
        }
        
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }
        
        // Verify new passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New passwords do not match");
        }
        
        // Verify new password is different from current
        if (passwordEncoder.matches(request.getNewPassword(), member.getPassword())) {
            throw new RuntimeException("New password must be different from current password");
        }
        
        // Check against last 5 passwords in history
        List<PasswordHistory> passwordHistory = passwordHistoryRepository.findTop5ByMember_IdOrderByChangedAtDesc(memberId);
        for (PasswordHistory ph : passwordHistory) {
            if (passwordEncoder.matches(request.getNewPassword(), ph.getPasswordHash())) {
                throw new RuntimeException("New password cannot be one of your last 5 passwords");
            }
        }
        
        // Save current password to history before changing
        PasswordHistory history = PasswordHistory.builder()
                .member(member)
                .passwordHash(member.getPassword())
                .changedAt(LocalDateTime.now())
                .build();
        passwordHistoryRepository.save(history);
        
        // Clean up old password history entries (keep only last 5)
        passwordHistoryRepository.deleteOldEntriesKeepingLast5(memberId);
        
        // Update password
        member.setPassword(passwordEncoder.encode(request.getNewPassword()));
        member.setMustChangePassword(false);
        memberRepository.save(member);
    }

    private AuthResponse buildSuperAdminResponse(Member member) {
        // Super admin gets FULL_ACCESS authority
        List<String> tokenPermissions = List.of("FULL_ACCESS");
        
        String token = jwtUtil.generateToken(
                member.getEmail(),
                member.getId(),
                0L,
                "Super Admin",
                true,
                tokenPermissions
        );

        return AuthResponse.builder()
                .token(token)
                .id(member.getId())
                .name(member.getName())
                .email(member.getEmail())
                .phone(member.getPhone())
                .tenantId(0L)
                .tenantName("Platform")
                .roleName("Super Admin")
                .superAdmin(true)
                .multiTenant(false)
                .permissions(getFullPermissions())
                .permissionCodes(tokenPermissions)
                .avatarInitials(getInitials(member.getName()))
                .mustChangePassword(Boolean.TRUE.equals(member.getMustChangePassword()))
                .build();
    }

    private AuthResponse buildMultiTenantResponse(Member member, List<Member> memberships) {
        List<TenantSelectionDto> tenants = memberships.stream()
                .map(m -> TenantSelectionDto.builder()
                        .id(m.getTenant().getId())
                        .name(m.getTenant().getName())
                        .code(m.getTenant().getCode())
                        .roleName(m.getRole() != null ? m.getRole().getName() : "Member")
                        .build())
                .collect(Collectors.toList());

        return AuthResponse.builder()
                .id(member.getId())
                .name(member.getName())
                .email(member.getEmail())
                .multiTenant(true)
                .availableTenants(tenants)
                .avatarInitials(getInitials(member.getName()))
                .build();
    }

    private AuthResponse buildAuthResponse(Member member, Tenant tenant) {
        String roleName = member.getRole() != null ? member.getRole().getName() : "Member";
        
        // Build permission strings for JWT token
        List<String> tokenPermissions = buildTokenPermissions(member);
        
        String token = jwtUtil.generateToken(
                member.getEmail(),
                member.getId(),
                tenant.getId(),
                roleName,
                false,
                tokenPermissions
        );

        // Legacy permissions list (kept for backward compatibility but no longer used)
        List<PermissionDto> permissions = new ArrayList<>();

        return AuthResponse.builder()
                .token(token)
                .id(member.getId())
                .name(member.getName())
                .email(member.getEmail())
                .phone(member.getPhone())
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .roleName(roleName)
                .superAdmin(false)
                .multiTenant(false)
                .permissions(permissions)
                .permissionCodes(tokenPermissions)
                .avatarInitials(getInitials(member.getName()))
                .mustChangePassword(Boolean.TRUE.equals(member.getMustChangePassword()))
                .sessionTimeoutMinutes(tenant.getSessionTimeoutMinutes())
                .build();
    }

    /**
     * Build permission strings for JWT token from member's role permissions.
     * Uses ONLY the new granular permission codes (e.g., VIEW_MEMBERS, DISBURSE_LOAN).
     */
    private List<String> buildTokenPermissions(Member member) {
        List<String> tokenPermissions = new ArrayList<>();
        
        if (member.getRole() == null) {
            return tokenPermissions;
        }
        
        // Use new granular permission codes from RolePermission entity
        if (member.getRole().getRolePermissions() != null) {
            for (var rp : member.getRole().getRolePermissions()) {
                tokenPermissions.add(rp.getPermissionCode());
            }
        }
        
        return tokenPermissions;
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) {
                initials.append(parts[i].charAt(0));
            }
        }
        return initials.toString().toUpperCase();
    }

    private List<PermissionDto> getFullPermissions() {
        List<String> modules = List.of("dashboard", "members", "meetings", "contributions", 
                "loans", "merry-go-round", "shares", "welfare", "investments", "finance", "roles", "settings");
        List<PermissionDto> permissions = new ArrayList<>();
        for (String module : modules) {
            permissions.add(PermissionDto.builder()
                    .module(module)
                    .view(true)
                    .create(true)
                    .edit(true)
                    .approve(true)
                    .delete(true)
                    .build());
        }
        return permissions;
    }

    /**
     * Get current user info for session validation.
     * Called by /auth/me endpoint to verify token and return user details.
     * Validates that memberId and email both match to prevent cross-database confusion.
     * Throws exception if member not found or mismatch (triggers 401 on frontend).
     */
    @Transactional(readOnly = true)
    public AuthResponse getCurrentUserInfo(Long memberId, String tokenEmail) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify email matches - prevents using token from different database
        // where the same memberId might exist but for a different person
        if (tokenEmail != null && !tokenEmail.equals(member.getEmail())) {
            throw new RuntimeException("Session invalid - user mismatch");
        }

        if (member.getStatus() != Member.MemberStatus.ACTIVE) {
            throw new RuntimeException("Account is not active");
        }

        // Super admin case
        if (member.isSuperAdmin()) {
            return buildSuperAdminResponse(member);
        }

        // Regular user - must have tenant
        Tenant tenant = member.getTenant();
        if (tenant == null) {
            throw new RuntimeException("No tenant associated with this account");
        }

        // Return full auth response (but no new token - just validate existing)
        return buildAuthResponse(member, tenant);
    }
}
