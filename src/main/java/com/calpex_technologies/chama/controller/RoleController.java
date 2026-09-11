package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.PermissionDto;
import com.calpex_technologies.chama.dto.RoleDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<List<RoleDto>> getAllRoles(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(roleService.getAllRoles(principal.getTenantId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDto> getRole(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(roleService.getRole(id, principal.getTenantId()));
    }

    @PostMapping
    public ResponseEntity<RoleDto> createRole(@RequestBody RoleDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(roleService.createRole(dto, principal.getTenantId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDto> updateRole(@PathVariable Long id, @RequestBody RoleDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(roleService.updateRole(id, dto, principal.getTenantId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        roleService.deleteRole(id, principal.getTenantId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<RoleDto> updateRolePermissions(
            @PathVariable Long id,
            @RequestBody List<PermissionDto> permissions,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(roleService.updateRolePermissions(id, permissions, principal.getTenantId()));
    }

    /**
     * Update role with permission codes (new granular permission system)
     */
    @PutMapping("/{id}/permission-codes")
    public ResponseEntity<RoleDto> updateRolePermissionCodes(
            @PathVariable Long id,
            @RequestBody List<String> permissionCodes,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(roleService.updateRolePermissionCodes(id, permissionCodes, principal.getTenantId()));
    }
}
