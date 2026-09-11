package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanProduct {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @Column(nullable = false)
    private String name; // e.g. "Emergency Loan", "Development Loan"
    
    private String description;
    
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate; // e.g. 10.00 for 10%
    
    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false, length = 20)
    @Builder.Default
    private InterestType interestType = InterestType.REDUCING_BALANCE;
    
    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount; // Maximum loan amount
    
    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount; // Minimum loan amount
    
    @Column(name = "max_multiplier", precision = 5, scale = 2)
    private BigDecimal maxMultiplier; // e.g. 3x savings
    
    // Repayment period settings
    @Column(name = "repayment_period", nullable = false)
    private Integer repaymentPeriod; // Number of periods
    
    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_period_type", nullable = false, length = 10)
    @Builder.Default
    private PeriodType repaymentPeriodType = PeriodType.MONTHS;
    
    @Column(name = "required_guarantors")
    @Builder.Default
    private Integer requiredGuarantors = 0;
    
    // Penalty settings
    @Column(name = "penalty_enabled", nullable = false)
    @Builder.Default
    private boolean penaltyEnabled = false;
    
    @Column(name = "penalty_rate", precision = 5, scale = 2)
    private BigDecimal penaltyRate; // e.g. 10.00 for 10% of principal or outstanding
    
    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", length = 20)
    @Builder.Default
    private PenaltyType penaltyType = PenaltyType.PERCENTAGE_OF_OUTSTANDING;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_frequency", length = 20)
    @Builder.Default
    private PenaltyFrequency penaltyFrequency = PenaltyFrequency.FLAT;
    
    @Column(name = "grace_period_days")
    @Builder.Default
    private Integer gracePeriodDays = 0; // Days after due date before penalty
    
    // Approval workflow
    @Column(name = "requires_approval", nullable = false)
    @Builder.Default
    private boolean requiresApproval = true;
    
    @Column(name = "approval_levels")
    @Builder.Default
    private Integer approvalLevels = 1; // Number of approval levels (1, 2, or 3)
    
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum InterestType {
        FLAT,              // Interest calculated on principal only
        REDUCING_BALANCE   // Interest calculated on outstanding balance
    }
    
    public enum PeriodType {
        DAYS,
        MONTHS
    }
    
    public enum PenaltyType {
        PERCENTAGE_OF_PRINCIPAL,    // e.g. 10% of original principal
        PERCENTAGE_OF_OUTSTANDING,  // e.g. 10% of current balance
        FIXED_AMOUNT               // Fixed amount per period
    }
    
    public enum PenaltyFrequency {
        FLAT,       // One-time penalty
        PER_PERIOD  // Penalty applied each period (month/day) overdue
    }
}
