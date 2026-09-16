package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * ShareSettings defines how member shares are calculated for a tenant.
 * 
 * 1. Share Percentage: Based on selected contribution types
 *    - Member's share % = (Member's contributions in selected types) / (Total contributions in selected types) × 100
 * 
 * 2. Member Value: Based on selected profit sources
 *    - Total pool = Sum of all selected profit sources
 *    - Member's value = Share % × Total pool
 */
@Entity
@Table(name = "share_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ShareSettings extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    // ==================== SHARE PERCENTAGE CALCULATION ====================
    // Which contribution types count toward share percentage calculation
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "share_contribution_types", joinColumns = @JoinColumn(name = "share_settings_id"))
    @Column(name = "contribution_type_id")
    @Builder.Default
    private Set<Long> shareContributionTypeIds = new HashSet<>();

    // ==================== PROFIT POOL CALCULATION ====================
    // Which sources count toward the total profit pool for value calculation
    
    // Contribution types that count toward profit pool (can be different from share calculation)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pool_contribution_types", joinColumns = @JoinColumn(name = "share_settings_id"))
    @Column(name = "contribution_type_id")
    @Builder.Default
    private Set<Long> poolContributionTypeIds = new HashSet<>();

    // Include fines collected in profit pool
    @Column(name = "include_fines")
    @Builder.Default
    private Boolean includeFines = false;

    // Include loan interest/profits in profit pool
    @Column(name = "include_loan_interest")
    @Builder.Default
    private Boolean includeLoanInterest = false;

    // Include investment returns in profit pool
    @Column(name = "include_investment_returns")
    @Builder.Default
    private Boolean includeInvestmentReturns = false;

    // Include penalties collected in profit pool
    @Column(name = "include_penalties")
    @Builder.Default
    private Boolean includePenalties = false;

    // Include processing fees collected in profit pool
    @Column(name = "include_processing_fees")
    @Builder.Default
    private Boolean includeProcessingFees = false;

    // ==================== METADATA ====================
    
    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

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
}
