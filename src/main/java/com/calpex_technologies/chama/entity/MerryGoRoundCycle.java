package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "merry_go_round_cycles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MerryGoRoundCycle extends TenantAwareEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "contribution_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal contributionAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CycleStatus status = CycleStatus.ACTIVE;

    // ==================== CYCLE TYPE SETTINGS ====================
    
    // If true, cycle repeats indefinitely; if false, runs for numberOfCycles
    @Column(name = "is_recurring")
    @Builder.Default
    private Boolean isRecurring = false;
    
    // Number of cycles to run (only used if isRecurring = false)
    // Each participant gets payout once per cycle
    @Column(name = "number_of_cycles")
    @Builder.Default
    private Integer numberOfCycles = 1;
    
    // Current cycle number (1, 2, 3...) - increments when all participants have received
    @Column(name = "current_cycle_number")
    @Builder.Default
    private Integer currentCycleNumber = 1;

    // ==================== FREQUENCY SETTINGS ====================
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Frequency frequency = Frequency.MONTHLY;
    
    // For WEEKLY: which day of week (1=Monday, 7=Sunday)
    @Column(name = "day_of_week")
    private Integer dayOfWeek;
    
    // For MONTHLY: which day of month (1-31) or null if using week-based
    @Column(name = "day_of_month")
    private Integer dayOfMonth;
    
    // For MONTHLY week-based: which week (1=first, 2=second, 3=third, 4=fourth, -1=last)
    @Column(name = "week_of_month")
    private Integer weekOfMonth;
    
    // For MONTHLY week-based: which day of that week (1=Monday, 7=Sunday)
    @Column(name = "day_of_week_in_month")
    private Integer dayOfWeekInMonth;

    // ==================== FINE SETTINGS ====================
    
    @Column(name = "late_payment_fine_amount", precision = 15, scale = 2)
    private BigDecimal latePaymentFineAmount;
    
    @Column(name = "auto_impose_fine")
    @Builder.Default
    private Boolean autoImposeFine = false;
    
    @Column(name = "grace_period_days")
    @Builder.Default
    private Integer gracePeriodDays = 0;

    // ==================== RELATIONSHIPS ====================

    @OneToMany(mappedBy = "cycle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MerryGoRoundRound> rounds = new ArrayList<>();
    
    @OneToMany(mappedBy = "cycle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<MerryGoRoundParticipant> participants = new HashSet<>();
    
    @OneToMany(mappedBy = "cycle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MerryGoRoundContribution> contributions = new ArrayList<>();
    
    @OneToMany(mappedBy = "cycle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MerryGoRoundFine> fines = new ArrayList<>();

    public enum CycleStatus {
        ACTIVE, COMPLETED, CANCELLED
    }
    
    public enum Frequency {
        DAILY, WEEKLY, MONTHLY
    }
}
