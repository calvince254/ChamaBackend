package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "investment_topups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InvestmentTopup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "topup_date", nullable = false)
    private LocalDate topupDate;

    @Column(name = "previous_principal", precision = 15, scale = 2, nullable = false)
    private BigDecimal previousPrincipal;

    @Column(name = "new_principal", precision = 15, scale = 2, nullable = false)
    private BigDecimal newPrincipal;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_id")
    private Member recordedBy;

    @Column(name = "recorded_by_name")
    private String recordedByName;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;
}
