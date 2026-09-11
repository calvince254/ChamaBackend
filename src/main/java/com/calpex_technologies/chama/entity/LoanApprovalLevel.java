package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Defines which member is responsible for each approval level in a loan product.
 * For example:
 * - Level 1: John (Treasurer - first approver)
 * - Level 2: Jane (Secretary - second approver)
 * - Level 3: Mike (Chairperson - final approver)
 */
@Entity
@Table(name = "loan_approval_levels", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"loan_product_id", "level"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanApprovalLevel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_product_id", nullable = false)
    private LoanProduct loanProduct;
    
    @Column(nullable = false)
    private Integer level; // 1, 2, or 3
    
    // Specific member assigned as approver at this level
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
    
    // Keep role for backward compatibility / fallback
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;
    
    @Column(name = "level_name")
    private String levelName; // e.g. "First Approver", "Final Approver"
    
    @Column(name = "can_reject", nullable = false)
    @Builder.Default
    private boolean canReject = true; // Can this level reject outright?
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
