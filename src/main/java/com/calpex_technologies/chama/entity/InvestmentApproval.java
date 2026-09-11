package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "investment_approvals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InvestmentApproval extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approver_id", nullable = false)
    private Member approver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalDecision decision = ApprovalDecision.PENDING;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(columnDefinition = "TEXT")
    private String comments;

    public enum ApprovalDecision {
        PENDING,    // Not yet voted
        APPROVED,   // Voted yes
        REJECTED    // Voted no
    }
}
