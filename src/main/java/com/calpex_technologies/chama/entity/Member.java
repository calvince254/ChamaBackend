package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Member extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = true)  // Nullable for super admin
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "member_no", nullable = false)
    private String memberNo;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MemberStatus status = MemberStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "total_savings", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalSavings = BigDecimal.ZERO;

    @Column(name = "total_shares", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalShares = BigDecimal.ZERO;

    @Column(name = "outstanding_loan", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal outstandingLoan = BigDecimal.ZERO;

    @Column(name = "arrears_months")
    @Builder.Default
    private int arrearsMonths = 0;

    @Column(name = "is_super_admin")
    @Builder.Default
    private boolean superAdmin = false;

    @Column(name = "must_change_password")
    @Builder.Default
    private Boolean mustChangePassword = false;

    public enum MemberStatus {
        ACTIVE, SUSPENDED, EXITED
    }
}
