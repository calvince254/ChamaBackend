package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Permission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false)
    private String module;

    @Column(name = "can_view")
    @Builder.Default
    private boolean canView = false;

    @Column(name = "can_create")
    @Builder.Default
    private boolean canCreate = false;

    @Column(name = "can_edit")
    @Builder.Default
    private boolean canEdit = false;

    @Column(name = "can_approve")
    @Builder.Default
    private boolean canApprove = false;

    @Column(name = "can_delete")
    @Builder.Default
    private boolean canDelete = false;
}
