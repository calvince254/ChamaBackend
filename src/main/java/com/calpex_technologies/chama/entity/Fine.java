package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fine {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fine_type_id", nullable = false)
    private FineType fineType;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount; // Amount at time of fine (copied from FineType)
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'UNPAID'")
    @Builder.Default
    private FineStatus status = FineStatus.UNPAID;
    
    private String notes; // Optional notes about this fine
    
    @Column(name = "imposed_at", nullable = false)
    private LocalDateTime imposedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imposed_by")
    private Member imposedBy; // Admin who imposed the fine
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    
    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount;
    
    @PrePersist
    protected void onCreate() {
        if (imposedAt == null) {
            imposedAt = LocalDateTime.now();
        }
    }
    
    public enum FineStatus {
        UNPAID,
        PARTIAL,
        PAID,
        WAIVED
    }
}
