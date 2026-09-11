package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Stores individual permission codes assigned to a role.
 * Each permission code corresponds to a specific endpoint/action (e.g., VIEW_MEMBERS, DISBURSE_LOAN).
 */
@Entity
@Table(name = "role_permissions", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RolePermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * The permission code (e.g., VIEW_MEMBERS, DISBURSE_LOAN, APPROVE_WELFARE_CLAIM)
     */
    @Column(name = "permission_code", nullable = false)
    private String permissionCode;
}
