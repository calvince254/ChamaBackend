package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.CreateTenantRequest;
import com.calpex_technologies.chama.dto.MemberDto;
import com.calpex_technologies.chama.dto.RoleDto;
import com.calpex_technologies.chama.dto.TenantDto;
import com.calpex_technologies.chama.dto.TenantSettingsDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    public ResponseEntity<List<TenantDto>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantDto> getTenant(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getTenant(id));
    }

    @PostMapping
    public ResponseEntity<TenantDto> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity.ok(tenantService.createTenantWithAdmin(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantDto> updateTenant(@PathVariable Long id, @RequestBody TenantDto dto) {
        return ResponseEntity.ok(tenantService.updateTenant(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspendTenant(@PathVariable Long id) {
        tenantService.suspendTenant(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateTenant(@PathVariable Long id) {
        tenantService.activateTenant(id);
        return ResponseEntity.noContent().build();
    }

    // Get members of a specific tenant (for super admin)
    @GetMapping("/{id}/members")
    public ResponseEntity<List<MemberDto>> getTenantMembers(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getTenantMembers(id));
    }

    // Get roles of a specific tenant (for assigning roles to members)
    @GetMapping("/{id}/roles")
    public ResponseEntity<List<RoleDto>> getTenantRoles(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getTenantRolesDto(id));
    }

    // Update member role within a tenant
    @PutMapping("/{tenantId}/members/{memberId}/role")
    public ResponseEntity<MemberDto> updateMemberRole(
            @PathVariable Long tenantId,
            @PathVariable Long memberId,
            @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(tenantService.updateMemberRole(tenantId, memberId, request.getRoleId()));
    }

    // Remove member from tenant
    @DeleteMapping("/{tenantId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long tenantId, @PathVariable Long memberId) {
        tenantService.removeMemberFromTenant(tenantId, memberId);
        return ResponseEntity.noContent().build();
    }

    // ==================== TENANT SETTINGS (for regular users) ====================
    
    /**
     * Get settings for the current user's tenant (General Settings tab).
     * Regular users can only access their own tenant's settings.
     */
    @GetMapping("/settings")
    public ResponseEntity<TenantSettingsDto> getMyTenantSettings(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(tenantService.getTenantSettings(principal.getTenantId()));
    }

    /**
     * Update settings for the current user's tenant.
     * Requires EDIT_TENANT_SETTINGS permission.
     */
    @PutMapping("/settings")
    public ResponseEntity<TenantSettingsDto> updateMyTenantSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody TenantSettingsDto dto) {
        return ResponseEntity.ok(tenantService.updateTenantSettings(principal.getTenantId(), dto));
    }

    /**
     * Get settings for a specific tenant (super admin only).
     */
    @GetMapping("/{id}/settings")
    public ResponseEntity<TenantSettingsDto> getTenantSettings(@PathVariable Long id) {
        return ResponseEntity.ok(tenantService.getTenantSettings(id));
    }

    /**
     * Update settings for a specific tenant (super admin only).
     */
    @PutMapping("/{id}/settings")
    public ResponseEntity<TenantSettingsDto> updateTenantSettings(
            @PathVariable Long id,
            @RequestBody TenantSettingsDto dto) {
        return ResponseEntity.ok(tenantService.updateTenantSettings(id, dto));
    }
}

// Inner class for role update request
class UpdateRoleRequest {
    private Long roleId;
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}
