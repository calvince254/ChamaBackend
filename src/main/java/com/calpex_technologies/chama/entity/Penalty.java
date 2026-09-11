package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Entity
@Table(name = "penalties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Penalty {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 30)
    private PenaltyType penaltyType;
    
    @Column(nullable = false)
    private String reason; // Description e.g. "Late payment for Monthly Savings - August 2026"
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "paid_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PenaltyStatus status = PenaltyStatus.UNPAID;
    
    // Link to meeting (for MEETING_ABSENT penalties)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;
    
    // Link to contribution type (for LATE_PAYMENT penalties)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_type_id")
    private ContributionType contributionType;
    
    // Period this penalty relates to (for LATE_PAYMENT)
    @Column(name = "period_month")
    private YearMonth periodMonth;
    
    @Column(name = "imposed_at", nullable = false)
    private LocalDateTime imposedAt;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    
    @PrePersist
    protected void onCreate() {
        if (imposedAt == null) {
            imposedAt = LocalDateTime.now();
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
    }
    
    public enum PenaltyType {
        MEETING_ABSENT,    // Absent from meeting
        LATE_PAYMENT       // Late contribution payment
    }
    
    public enum PenaltyStatus {
        UNPAID,
        PARTIAL,
        PAID,
        WAIVED
    }
}
