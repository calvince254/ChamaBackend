package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "merry_go_round_participants", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"cycle_id", "member_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MerryGoRoundParticipant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id", nullable = false)
    private MerryGoRoundCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // Position in the payout order (1, 2, 3, etc.)
    @Column(name = "payout_order")
    private Integer payoutOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.ACTIVE;

    public enum ParticipantStatus {
        ACTIVE, REMOVED, RECEIVED_PAYOUT
    }
}
