package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fine_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FineType {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @Column(nullable = false)
    private String name; // e.g. "Late to meeting", "Missing contribution", "Phone ringing"
    
    private String description;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
    
    // Automatic fine settings
    @Column(name = "is_automatic", nullable = false)
    @Builder.Default
    private boolean automatic = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 30)
    private TriggerType triggerType;
    
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
    
    /**
     * Trigger types for automatic fines
     */
    public enum TriggerType {
        MEETING_ABSENT,      // Auto-imposed when member is marked absent from meeting
        LATE_PAYMENT         // Auto-imposed when contribution payment is late
    }
}
