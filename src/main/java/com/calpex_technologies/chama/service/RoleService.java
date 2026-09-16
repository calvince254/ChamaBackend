package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.PermissionDto;
import com.calpex_technologies.chama.dto.RoleDto;
import com.calpex_technologies.chama.entity.Permission;
import com.calpex_technologies.chama.entity.Role;
import com.calpex_technologies.chama.entity.RolePermission;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.PermissionRepository;
import com.calpex_technologies.chama.repository.RolePermissionRepository;
import com.calpex_technologies.chama.repository.RoleRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final TenantRepository tenantRepository;
    private final MemberRepository memberRepository;

    public List<RoleDto> getAllRoles(Long tenantId) {
        return roleRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public RoleDto getRole(Long id, Long tenantId) {
        Role role = roleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        return toDto(role);
    }

    @Transactional
    public RoleDto createRole(RoleDto dto, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Role role = Role.builder()
                .name(dto.getName())
                .systemDefault(false)
                .tenant(tenant)
                .build();

        role = roleRepository.save(role);

        // Add legacy permissions for backward compatibility
        if (dto.getPermissions() != null) {
            for (PermissionDto p : dto.getPermissions()) {
                Permission permission = Permission.builder()
                        .role(role)
                        .module(p.getModule())
                        .canView(p.isView())
                        .canCreate(p.isCreate())
                        .canEdit(p.isEdit())
                        .canApprove(p.isApprove())
                        .canDelete(p.isDelete())
                        .build();
                role.addPermission(permission);
            }
        }

        // Add new granular permission codes
        if (dto.getPermissionCodes() != null) {
            for (String code : dto.getPermissionCodes()) {
                RolePermission rp = RolePermission.builder()
                        .role(role)
                        .permissionCode(code)
                        .build();
                role.addRolePermission(rp);
            }
        }

        role = roleRepository.save(role);
        return toDto(role);
    }

    @Transactional
    public RoleDto updateRole(Long id, RoleDto dto, Long tenantId) {
        Role role = roleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if (role.isSystemDefault()) {
            throw new RuntimeException("Cannot modify system default role");
        }

        role.setName(dto.getName());

        // Update legacy permissions
        role.getPermissions().clear();
        if (dto.getPermissions() != null) {
            for (PermissionDto p : dto.getPermissions()) {
                Permission permission = Permission.builder()
                        .role(role)
                        .module(p.getModule())
                        .canView(p.isView())
                        .canCreate(p.isCreate())
                        .canEdit(p.isEdit())
                        .canApprove(p.isApprove())
                        .canDelete(p.isDelete())
                        .build();
                role.addPermission(permission);
            }
        }

        role = roleRepository.save(role);
        return toDto(role);
    }

    /**
     * Update only the permission codes of a role (new granular permission system)
     */
    @Transactional
    public RoleDto updateRolePermissions(Long id, List<PermissionDto> permissions, Long tenantId) {
        Role role = roleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if (role.isSystemDefault()) {
            throw new RuntimeException("Cannot modify system default role");
        }

        // Clear existing legacy permissions
        role.getPermissions().clear();
        
        // Add new legacy permissions
        if (permissions != null) {
            for (PermissionDto p : permissions) {
                Permission permission = Permission.builder()
                        .role(role)
                        .module(p.getModule())
                        .canView(p.isView())
                        .canCreate(p.isCreate())
                        .canEdit(p.isEdit())
                        .canApprove(p.isApprove())
                        .canDelete(p.isDelete())
                        .build();
                role.addPermission(permission);
            }
        }

        role = roleRepository.save(role);
        return toDto(role);
    }

    /**
     * Update role permissions using permission codes (new granular permission system)
     */
    @Transactional
    public RoleDto updateRolePermissionCodes(Long id, List<String> permissionCodes, Long tenantId) {
        Role role = roleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if (role.isSystemDefault()) {
            throw new RuntimeException("Cannot modify system default role");
        }

        // Delete existing role permissions explicitly first
        rolePermissionRepository.deleteByRoleId(role.getId());
        rolePermissionRepository.flush();
        
        // Clear the collection in memory
        role.getRolePermissions().clear();
        
        // Add new permission codes (avoid duplicates)
        if (permissionCodes != null) {
            for (String code : permissionCodes.stream().distinct().collect(Collectors.toList())) {
                RolePermission rp = RolePermission.builder()
                        .role(role)
                        .permissionCode(code)
                        .build();
                role.addRolePermission(rp);
            }
        }

        role = roleRepository.save(role);
        return toDto(role);
    }

    @Transactional
    public void deleteRole(Long id, Long tenantId) {
        Role role = roleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if (role.isSystemDefault()) {
            throw new RuntimeException("Cannot delete system default role");
        }

        // Check if any members are using this role
        long memberCount = memberRepository.findByTenantId(tenantId).stream()
                .filter(m -> m.getRole() != null && m.getRole().getId().equals(role.getId()))
                .count();
        
        if (memberCount > 0) {
            throw new RuntimeException("Cannot delete role '" + role.getName() + "' because it is assigned to " + memberCount + " member(s). Please reassign these members to a different role first.");
        }

        roleRepository.delete(role);
    }

    private RoleDto toDto(Role role) {
        long memberCount = memberRepository.findByTenantId(role.getTenant().getId()).stream()
                .filter(m -> m.getRole() != null && m.getRole().getId().equals(role.getId()))
                .count();

        // Legacy permissions
        List<PermissionDto> permissions = role.getPermissions().stream()
                .map(p -> PermissionDto.builder()
                        .module(p.getModule())
                        .view(p.isCanView())
                        .create(p.isCanCreate())
                        .edit(p.isCanEdit())
                        .approve(p.isCanApprove())
                        .delete(p.isCanDelete())
                        .build())
                .collect(Collectors.toList());

        // New granular permission codes
        List<String> permissionCodes = role.getRolePermissions().stream()
                .map(RolePermission::getPermissionCode)
                .collect(Collectors.toList());

        return RoleDto.builder()
                .id(role.getId())
                .name(role.getName())
                .isSystemDefault(role.isSystemDefault())
                .permissions(permissions)
                .permissionCodes(permissionCodes)
                .memberCount((int) memberCount)
                .build();
    }
}
