package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * License configuration for a tenant/chama.
 * Super admin sets percentage fees that will be charged on various transaction types.
 */
@Entity
@Table(name = "licenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class License extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    /**
     * Percentage charged on all savings/contributions (e.g., 5.0 means 5%)
     */
    @Column(name = "contribution_fee_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal contributionFeePercent = BigDecimal.ZERO;

    /**
     * Percentage charged on all fines collected
     */
    @Column(name = "fine_fee_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal fineFeePercent = BigDecimal.ZERO;

    /**
     * Percentage charged on loan disbursements
     */
    @Column(name = "loan_fee_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal loanFeePercent = BigDecimal.ZERO;

    /**
     * Percentage charged on loan interest collected
     */
    @Column(name = "loan_interest_fee_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal loanInterestFeePercent = BigDecimal.ZERO;

    /**
     * Percentage charged on penalty payments
     */
    @Column(name = "penalty_fee_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal penaltyFeePercent = BigDecimal.ZERO;

    /**
     * Fixed monthly fee (in addition to percentage fees)
     */
    @Column(name = "monthly_fixed_fee", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal monthlyFixedFee = BigDecimal.ZERO;

    /**
     * Grace period in days after invoice due date before suspension
     */
    @Column(name = "grace_period_days")
    @Builder.Default
    private Integer gracePeriodDays = 7;

    /**
     * Day of month when billing cycle starts (based on tenant onboarding date)
     */
    @Column(name = "billing_day_of_month")
    private Integer billingDayOfMonth;

    /**
     * Whether license is active
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LicenseStatus status = LicenseStatus.ACTIVE;

    /**
     * Notes from super admin
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    public enum LicenseStatus {
        ACTIVE,           // Normal operation
        SUSPENDED,        // Payment overdue, chama features disabled
        TRIAL,            // Trial period
        CANCELLED         // License cancelled
    }
}
