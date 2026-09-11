package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Role extends TenantAwareEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "is_system_default")
    @Builder.Default
    private boolean systemDefault = false;

    /**
     * Legacy permissions (module + action flags) - kept for backward compatibility
     */
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    /**
     * New granular permissions (individual permission codes like VIEW_MEMBERS, DISBURSE_LOAN)
     */
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<RolePermission> rolePermissions = new HashSet<>();

    public void addPermission(Permission permission) {
        permissions.add(permission);
        permission.setRole(this);
    }

    public void removePermission(Permission permission) {
        permissions.remove(permission);
        permission.setRole(null);
    }

    public void addRolePermission(RolePermission rolePermission) {
        rolePermissions.add(rolePermission);
        rolePermission.setRole(this);
    }

    public void removeRolePermission(RolePermission rolePermission) {
        rolePermissions.remove(rolePermission);
        rolePermission.setRole(null);
    }

    /**
     * Check if this role has a specific permission code
     */
    public boolean hasPermission(String permissionCode) {
        return rolePermissions.stream()
                .anyMatch(rp -> rp.getPermissionCode().equals(permissionCode));
    }
}
