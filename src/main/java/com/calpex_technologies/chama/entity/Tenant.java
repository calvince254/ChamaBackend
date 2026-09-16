package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Tenant extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TenantPlan plan = TenantPlan.FREE;

    // ==================== CHAMA SETTINGS ====================
    
    @Column(name = "registration_fee", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal registrationFee = BigDecimal.ZERO;

    @Column(name = "registration_fee_required")
    @Builder.Default
    private Boolean registrationFeeRequired = false;

    @Column(name = "min_members")
    @Builder.Default
    private Integer minMembers = 3;

    @Column(name = "max_members")
    @Builder.Default
    private Integer maxMembers = 50;

    @Column(name = "signatures_required")
    @Builder.Default
    private Integer signaturesRequired = 2;

    // Session timeout in minutes (0 = no timeout, default 30 minutes)
    @Column(name = "session_timeout_minutes")
    @Builder.Default
    private Integer sessionTimeoutMinutes = 30;

    public enum TenantStatus {
        ACTIVE, SUSPENDED, PENDING
    }

    public enum TenantPlan {
        FREE, STANDARD, PREMIUM
    }
}
