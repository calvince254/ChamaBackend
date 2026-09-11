package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "merry_go_round_fines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MerryGoRoundFine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id", nullable = false)
    private MerryGoRoundCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_id")
    private MerryGoRoundContribution contribution;

    @Column(nullable = false)
    private String reason;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "imposed_date", nullable = false)
    private LocalDate imposedDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FineStatus status = FineStatus.PENDING;

    @Column(name = "auto_imposed")
    @Builder.Default
    private Boolean autoImposed = false;

    @Column(name = "waived_by")
    private String waivedBy;

    @Column(name = "waive_reason")
    private String waiveReason;

    public enum FineStatus {
        PENDING, PAID, WAIVED
    }
}
