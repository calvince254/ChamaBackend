package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "contribution_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributionType {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @Column(nullable = false)
    private String name; // e.g. "Monthly Savings", "Principal Contribution", "Emergency Fund"
    
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    @Builder.Default
    private Category category = Category.NORMAL; // NORMAL, WELFARE, EMERGENCY, OTHER
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount; // Amount due per period
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Frequency frequency;
    
    @Column(name = "due_day")
    private Integer dueDay; // Day of month (1-28) when payment is due
    
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
    
    // Late payment penalty settings
    @Column(name = "late_fine_enabled", nullable = false)
    @Builder.Default
    private boolean lateFineEnabled = false;
    
    @Column(name = "late_fine_amount", precision = 10, scale = 2)
    private BigDecimal lateFineAmount; // Penalty for late payment
    
    @Column(name = "grace_period_days")
    @Builder.Default
    private Integer gracePeriodDays = 0; // Days after due date before penalty applies
    
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
    
    public enum Frequency {
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }
    
    public enum Category {
        NORMAL,
        WELFARE,
        EMERGENCY,
        OTHER
    }
}
