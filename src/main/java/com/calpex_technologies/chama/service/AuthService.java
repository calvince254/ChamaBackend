package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import com.calpex_technologies.chama.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Find all members by email (across all tenants)
        List<Member> members = memberRepository.findAllByEmail(request.getEmail());
        
        if (members.isEmpty()) {
            throw new RuntimeException("Invalid credentials");
        }
        
        // Find a member with matching password
        Member validMember = null;
        for (Member m : members) {
            if (passwordEncoder.matches(request.getPassword(), m.getPassword())) {
                validMember = m;
                break;
            }
        }
        
        if (validMember == null) {
            throw new RuntimeException("Invalid credentials");
        }

        if (validMember.getStatus() != Member.MemberStatus.ACTIVE) {
            throw new RuntimeException("Account is not active");
        }

        // Check if super admin (no tenant)
        if (validMember.isSuperAdmin()) {
            return buildSuperAdminResponse(validMember);
        }

        // Get all tenants this user belongs to
        List<Member> activeMemberships = members.stream()
                .filter(m -> m.getStatus() == Member.MemberStatus.ACTIVE)
                .filter(m -> m.getTenant() != null)
                .filter(m -> passwordEncoder.matches(request.getPassword(), m.getPassword()))
                .collect(Collectors.toList());
        
        // If user is in multiple chamas, return selection list
        if (activeMemberships.size() > 1) {
            return buildMultiTenantResponse(validMember, activeMemberships);
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
        Member member = memberRepository.findByEmailAndTenantId(request.getEmail(), tenantId)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

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
}
